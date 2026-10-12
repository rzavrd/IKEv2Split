#!/usr/bin/env python3
"""
MYLO VPN account + admin server. Python 3.8+, nothing to install.

Run it:
  python3 mylo_server.py admin-token                       create the admin token (shown once; use --new to replace it)
  python3 mylo_server.py serve --port 8080 --trust-proxy --public-url https://api.your-domain.com

Then open  https://api.your-domain.com/admin  and paste the admin token: users, OpenVPN files, servers and app updates
are managed there. The same things are available as an HTTP API (see server/README.md) and as commands:

  python3 mylo_server.py add ali --days 30 --devices 2     create a user (prints the password)
  python3 mylo_server.py list | disable|enable|delete ali | passwd ali | extend ali --days 30
  python3 mylo_server.py devices ali / reset-devices ali / kick ali
  python3 mylo_server.py ovpn-import FILE_OR_DIR_OR_ZIP ...  add many .ovpn files at once (certificates are put inside)
  python3 mylo_server.py list-ovpn / remove-ovpn NAME

The VPN account, the server list and the .ovpn files live in ./data (or $MYLO_DATA). They are only handed out to
signed-in, enabled, non-expired users.
"""
import argparse, gzip, hashlib, hmac, io, json, os, posixpath, re, secrets, sqlite3, ssl, sys, tempfile, threading, time, zipfile
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from urllib.parse import urlparse, parse_qs, unquote

BASE = Path(__file__).resolve().parent
DATA = Path(os.environ.get("MYLO_DATA") or (BASE / "data"))
DB_PATH = DATA / "mylo.db"
CONFIG = DATA / "vpn.json"
OVPN_DIR = DATA / "openvpn"
UPDATE_DIR = DATA / "update"
ADMIN_FILE = DATA / "admin.json"
DAY = 86400
FAILS = {}            # (ip, username) -> [timestamps]
MAX_FAILS, WINDOW = 8, 600
MAX_OVPN = 5000                    # profiles
MAX_OVPN_FILE = 512 * 1024         # bytes per file
MAX_APK = 200 * 1024 * 1024
PUBLIC_URL = (os.environ.get("MYLO_PUBLIC_URL") or "").rstrip("/")
SSL_ON = False
LOCK = threading.RLock()


# ---------------------------------------------------------------- small helpers
def atomic_write(path, data):
    path = Path(path)
    path.parent.mkdir(parents=True, exist_ok=True)
    tmp = path.with_name(path.name + ".tmp-" + secrets.token_hex(4))
    if isinstance(data, (bytes, bytearray)):
        tmp.write_bytes(bytes(data))
    else:
        tmp.write_text(data, encoding="utf-8")
    os.replace(str(tmp), str(path))


def remove_suffix(s, suf):
    return s[:-len(suf)] if suf and s.endswith(suf) else s


def safe_stem(name):
    s = "".join(c if (c.isalnum() or c in "-_") else "-" for c in name).strip("-")
    return s[:60] or "profile"


def cc_of(name):
    return name[:2].upper() if len(name) > 2 and name[2] in "-_ " and name[:2].isalpha() else ""


# ---------------------------------------------------------------- storage
def db():
    DATA.mkdir(parents=True, exist_ok=True)
    c = sqlite3.connect(str(DB_PATH), timeout=10)
    c.row_factory = sqlite3.Row
    c.executescript("""
    CREATE TABLE IF NOT EXISTS users(
      id INTEGER PRIMARY KEY, username TEXT UNIQUE NOT NULL, pw TEXT NOT NULL,
      enabled INTEGER NOT NULL DEFAULT 1, expires INTEGER NOT NULL DEFAULT 0,
      max_devices INTEGER NOT NULL DEFAULT 2, created INTEGER NOT NULL, note TEXT DEFAULT '');
    CREATE TABLE IF NOT EXISTS devices(
      user_id INTEGER NOT NULL, device_id TEXT NOT NULL, model TEXT DEFAULT '',
      first_seen INTEGER NOT NULL, last_seen INTEGER NOT NULL, PRIMARY KEY(user_id, device_id));
    CREATE TABLE IF NOT EXISTS tokens(
      token_hash TEXT PRIMARY KEY, user_id INTEGER NOT NULL, device_id TEXT NOT NULL,
      created INTEGER NOT NULL, last_seen INTEGER NOT NULL);
    """)
    return c


def hash_pw(pw, salt=None, algo=None):
    salt = salt or secrets.token_bytes(16)
    algo = algo or ("scrypt" if hasattr(hashlib, "scrypt") else "pbkdf2")
    if algo == "scrypt":
        h = hashlib.scrypt(pw.encode(), salt=salt, n=2 ** 14, r=8, p=1, dklen=32)
    else:
        h = hashlib.pbkdf2_hmac("sha256", pw.encode(), salt, 300_000, 32)
    return f"{algo}${salt.hex()}${h.hex()}"


def check_pw(pw, stored):
    try:
        algo, salt, h = stored.split("$")
        return hmac.compare_digest(hash_pw(pw, bytes.fromhex(salt), algo), stored)
    except Exception:
        return False


def new_password(n=12):
    alphabet = "abcdefghjkmnpqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ23456789"
    return "".join(secrets.choice(alphabet) for _ in range(n))


def tok_hash(t):
    return hashlib.sha256(t.encode()).hexdigest()


# ---------------------------------------------------------------- admin token
def admin_token_ok(tok):
    if not tok:
        return False
    env = os.environ.get("MYLO_ADMIN_TOKEN")
    if env and hmac.compare_digest(tok, env):
        return True
    try:
        h = json.loads(ADMIN_FILE.read_text()).get("hash", "")
    except Exception:
        return False
    return bool(h) and hmac.compare_digest(tok_hash(tok), h)


def make_admin_token():
    t = "mylo_" + secrets.token_urlsafe(30)
    atomic_write(ADMIN_FILE, json.dumps({"hash": tok_hash(t), "created": int(time.time())}))
    try:
        os.chmod(str(ADMIN_FILE), 0o600)
    except Exception:
        pass
    return t


# ---------------------------------------------------------------- config + OpenVPN profiles
def read_cfg():
    try:
        c = json.loads(CONFIG.read_text(encoding="utf-8"))
    except (OSError, ValueError):
        c = {}
    c.setdefault("vpn", {})
    c.setdefault("servers", [])
    c.setdefault("message", "")
    return c


def write_cfg(c):
    atomic_write(CONFIG, json.dumps(c, indent=2, ensure_ascii=False) + "\n")


def ovpn_paths():
    return sorted(OVPN_DIR.glob("*.ovpn")) if OVPN_DIR.exists() else []


_BASE = {"sig": None, "json": "", "etag": ""}
_GZ = {}


def base_config():
    """The JSON handed to every signed-in user, cached until a file changes. Returns (json_text, etag_seed)."""
    sig = [CONFIG.stat().st_mtime_ns if CONFIG.exists() else 0]
    for f in ovpn_paths():
        try:
            st = f.stat()
            sig.append((f.name, st.st_mtime_ns, st.st_size))
        except OSError:
            pass
    with LOCK:
        if _BASE["sig"] == sig:
            return _BASE["json"], _BASE["etag"]
        cfg = read_cfg()
        ovpn = list(cfg.get("openvpn", []))
        for f in ovpn_paths():
            ovpn.append({"name": f.stem, "cc": cc_of(f.stem), "config": f.read_text(encoding="utf-8", errors="replace")})
        text = json.dumps({"vpn": cfg["vpn"], "servers": cfg["servers"], "openvpn": ovpn, "message": cfg.get("message", "")},
                          ensure_ascii=False)
        _BASE.update(sig=sig, json=text, etag=hashlib.sha1(text.encode()).hexdigest())
        return text, _BASE["etag"]


def config_body(expires):
    """(body_bytes, etag) for a user; expiresAt differs per user so it is part of the tag."""
    text, seed = base_config()
    body = (text[:-1] + ', "expiresAt": %d}' % int(expires or 0)).encode("utf-8")
    etag = '"%s"' % hashlib.sha1((seed + ":" + str(int(expires or 0))).encode()).hexdigest()
    return body, etag


# ---------------------------------------------------------------- OpenVPN file preparation (inline certificates, checks)
EXTERNAL = ("ca", "cert", "key", "tls-auth", "tls-crypt", "tls-crypt-v2", "pkcs12", "secret", "dh", "extra-certs")
_SPACES = re.compile(r"\s+")


def check_ovpn(text):
    """Returns (errors, warnings). The app only receives the text, so every file must be inside it."""
    errs, warns = [], []
    lines = [l.strip() for l in text.splitlines()]
    if not any(re.match(r"^remote\s+\S+", l) for l in lines):
        errs.append("no 'remote' line - this does not look like an OpenVPN profile")
    inline = {l[1:-1] for l in lines if l.startswith("<") and l.endswith(">") and not l.startswith("</")}
    for l in lines:
        if l.startswith(("#", ";")):
            continue
        w = _SPACES.split(l)
        if len(w) >= 2 and w[0] in EXTERNAL and w[0] not in inline:
            errs.append(f"'{w[0]} {w[1]}' points to a separate file that was not found; include it, or paste its content inside <{w[0]}>...</{w[0]}>")
    if "auth-user-pass" in [l.split()[0] for l in lines if l.split() and not l.startswith(("#", ";"))]:
        warns.append("profile has auth-user-pass: OpenVPN for Android will ask for the VPN username/password once")
    return errs, warns


def inline_externals(text, lookup):
    """Replaces 'ca ca.crt', 'tls-auth ta.key 1', 'auth-user-pass creds.txt' ... by the file contents.
    lookup(ref) -> text or None. Lines whose file is not found are left alone (check_ovpn then rejects the profile)."""
    out = []
    inline = {l.strip()[1:-1] for l in text.splitlines() if l.strip().startswith("<") and l.strip().endswith(">") and not l.strip().startswith("</")}
    for raw in text.replace("\r\n", "\n").replace("\r", "\n").split("\n"):
        s = raw.strip()
        w = _SPACES.split(s) if s else []
        if w and not s.startswith(("#", ";")):
            d = w[0]
            if d in EXTERNAL and len(w) >= 2 and d not in inline and d != "pkcs12":
                body = lookup(w[1])
                if body is not None and body.strip():
                    if d == "tls-auth" and len(w) >= 3:
                        out.append("key-direction " + w[2])
                    out.append("<%s>\n%s\n</%s>" % (d, body.strip().replace("\r\n", "\n"), d))
                    continue
            if d == "auth-user-pass" and len(w) >= 2:
                body = lookup(w[1])
                lines2 = [x.strip() for x in (body or "").splitlines() if x.strip()]
                if len(lines2) >= 2:
                    out.append("<auth-user-pass>\n%s\n%s\n</auth-user-pass>" % (lines2[0], lines2[1]))
                else:
                    out.append("auth-user-pass")      # the app asks for the credentials instead
                continue
        out.append(raw.rstrip("\r"))
    return "\n".join(out).strip() + "\n"


def collect_inputs(paths):
    """files / folders / .zip archives -> {relative posix path: bytes}"""
    files = {}
    for p in paths:
        p = Path(p)
        if p.is_dir():
            for f in sorted(p.rglob("*")):
                if f.is_file() and f.stat().st_size <= 2 * MAX_OVPN_FILE:
                    files[f.relative_to(p).as_posix()] = f.read_bytes()
        elif p.suffix.lower() == ".zip":
            files.update(read_zip(p))
        elif p.is_file():
            files[p.name] = p.read_bytes()
    return files


def read_zip(src, max_total=200 * 1024 * 1024):
    files = {}
    total = 0
    with zipfile.ZipFile(str(src)) as z:
        infos = [i for i in z.infolist() if not i.is_dir()]
        if len(infos) > MAX_OVPN * 3:
            raise ValueError("zip has too many files")
        for i in infos:
            name = i.filename.replace("\\", "/")
            if name.startswith("/") or ".." in name.split("/") or "__MACOSX" in name.split("/") or posixpath.basename(name).startswith("."):
                continue
            if i.file_size > 2 * MAX_OVPN_FILE:
                continue
            total += i.file_size
            if total > max_total:
                raise ValueError("zip is too big when unpacked")
            files[name] = z.read(i)
    return files


def prepare_ovpn(files):
    """files {path: bytes} -> ({name: text}, report[list of str]). Certificates / keys / credentials files that sit next to a
    profile are put inside it. Incomplete profiles are reported and skipped."""
    text_files = {k: v for k, v in files.items()}
    by_base = {}
    for k in text_files:
        by_base.setdefault(posixpath.basename(k).lower(), []).append(k)

    def dec(b):
        return b.decode("utf-8-sig", errors="replace").replace("\r\n", "\n").replace("\r", "\n")

    result, report, used = {}, [], set()
    for path in sorted(text_files):
        if not path.lower().endswith((".ovpn", ".conf")):
            continue
        raw = text_files[path]
        if len(raw) > MAX_OVPN_FILE:
            report.append(f"{path}: file is too big")
            continue
        folder = posixpath.dirname(path)

        def lookup(ref, folder=folder):
            ref = ref.replace("\\", "/").strip("\"'")
            cand = posixpath.normpath(posixpath.join(folder, ref))
            if cand in text_files:
                return dec(text_files[cand])
            hits = by_base.get(posixpath.basename(ref).lower(), [])
            hits = [h for h in hits if not h.lower().endswith((".ovpn", ".conf"))]
            return dec(text_files[hits[0]]) if hits else None

        text = inline_externals(dec(raw), lookup)
        errs, _ = check_ovpn(text)
        if errs:
            report.append(f"{path}: {errs[0]}")
            continue
        stem = safe_stem(remove_suffix(remove_suffix(posixpath.basename(path), ".ovpn"), ".conf"))
        name, k = stem, 2
        while name.lower() in used:
            name = f"{stem}-{k}"
            k += 1
        used.add(name.lower())
        result[name] = text
    return result, report


def save_ovpn(profiles):
    """{name: text} -> list of names written (same names are replaced)."""
    OVPN_DIR.mkdir(parents=True, exist_ok=True)
    with LOCK:
        existing = {f.stem for f in ovpn_paths()}
        new = [n for n in profiles if n not in existing]
        if len(existing) + len(new) > MAX_OVPN:
            raise ValueError(f"more than {MAX_OVPN} profiles")
        for n, t in profiles.items():
            atomic_write(OVPN_DIR / (n + ".ovpn"), t)
    return list(profiles)


def delete_ovpn(names):
    n = 0
    with LOCK:
        for name in names:
            f = OVPN_DIR / (safe_stem(remove_suffix(str(name), ".ovpn")) + ".ovpn")
            if f.exists():
                f.unlink()
                n += 1
    return n


# ---------------------------------------------------------------- app update hosting
APK_NAME = re.compile(r"^[0-9A-Za-z._-]{1,60}\.apk$")


def read_release():
    try:
        return json.loads((UPDATE_DIR / "meta.json").read_text())
    except (OSError, ValueError):
        return None


def apk_signed(path, names=None):
    """True when the APK carries a signature: the old META-INF/*.RSA|DSA|EC file (v1) or, for apps with minSdk >= 24 where
    Android Studio signs only with scheme v2/v3, the 'APK Sig Block 42' that sits in front of the zip central directory."""
    if names is None:
        with zipfile.ZipFile(path) as z:
            names = set(z.namelist())
    if any(n.startswith("META-INF/") and n.upper().endswith((".RSA", ".DSA", ".EC")) for n in names):
        return True
    try:
        with zipfile.ZipFile(path) as z:
            cd = z.start_dir
        with open(path, "rb") as f:
            if cd < 16:
                return False
            f.seek(cd - 16)
            return f.read(16) == b"APK Sig Block 42"
    except Exception:
        return False


def save_release(tmp_apk, version_code, version_name, notes, mandatory, force):
    """Validates an uploaded APK (a signed zip with AndroidManifest.xml) and makes it the current release."""
    if not zipfile.is_zipfile(tmp_apk):
        raise ValueError("this is not an APK file")
    with zipfile.ZipFile(tmp_apk) as z:
        names = set(z.namelist())
    if "AndroidManifest.xml" not in names or "classes.dex" not in names:
        raise ValueError("this is not an Android app (AndroidManifest.xml / classes.dex missing)")
    if not apk_signed(tmp_apk, names):
        raise ValueError("the APK is not signed - build the release variant with your key (Build > Build APK, variant release)")
    if not re.match(r"^[0-9A-Za-z._-]{1,30}$", version_name):
        raise ValueError("versionName may only contain letters, digits . _ -")
    with LOCK:
        cur = read_release()
        if cur and version_code <= int(cur.get("versionCode", 0)) and not force:
            raise ValueError(f"versionCode {version_code} is not higher than the published {cur['versionCode']} - phones would not update (use force to override)")
        h = hashlib.sha256()
        with open(tmp_apk, "rb") as f:
            for chunk in iter(lambda: f.read(1 << 20), b""):
                h.update(chunk)
        fname = f"mylo-{version_name}.apk"
        UPDATE_DIR.mkdir(parents=True, exist_ok=True)
        os.replace(tmp_apk, str(UPDATE_DIR / fname))
        meta = {"versionCode": int(version_code), "versionName": version_name, "file": fname, "sha256": h.hexdigest(),
                "notes": notes, "mandatory": bool(mandatory), "size": (UPDATE_DIR / fname).stat().st_size, "published": int(time.time())}
        atomic_write(UPDATE_DIR / "meta.json", json.dumps(meta, indent=2, ensure_ascii=False))
        keep = sorted(UPDATE_DIR.glob("mylo-*.apk"), key=lambda p: p.stat().st_mtime, reverse=True)
        for old in keep[4:]:
            try:
                old.unlink()
            except OSError:
                pass
    return meta


# ---------------------------------------------------------------- users (shared by the API)
def user_row(conn, name):
    return conn.execute("SELECT * FROM users WHERE username=?", (str(name).lower(),)).fetchone()


def users_list(conn):
    out = []
    for u in conn.execute("SELECT * FROM users ORDER BY username"):
        n = conn.execute("SELECT COUNT(*), MAX(last_seen) FROM devices WHERE user_id=?", (u["id"],)).fetchone()
        out.append({"username": u["username"], "enabled": bool(u["enabled"]), "expires": u["expires"], "maxDevices": u["max_devices"],
                    "devices": n[0], "lastSeen": n[1] or 0, "note": u["note"] or "", "created": u["created"]})
    return out


# ---------------------------------------------------------------- HTTP
class Handler(BaseHTTPRequestHandler):
    server_version = "mylo"
    protocol_version = "HTTP/1.1"
    trust_proxy = False

    def log_message(self, fmt, *a):
        sys.stderr.write("%s %s\n" % (time.strftime("%H:%M:%S"), fmt % a))

    def ip(self):
        if self.trust_proxy and self.headers.get("X-Forwarded-For"):
            return self.headers["X-Forwarded-For"].split(",")[0].strip()
        return self.client_address[0]

    # ---- responses
    def send_raw(self, code, body, ctype="application/json", extra=None):
        self.send_response(code)
        self.send_header("Content-Type", ctype)
        self.send_header("Content-Length", str(len(body)))
        self.send_header("Cache-Control", "no-store")
        self.send_header("X-Content-Type-Options", "nosniff")
        for k, v in (extra or {}).items():
            self.send_header(k, v)
        self.end_headers()
        if self.command != "HEAD":
            self.wfile.write(body)

    def send(self, code, obj):
        self.send_raw(code, json.dumps(obj, ensure_ascii=False).encode("utf-8"))

    def drain(self):
        """Read and drop an unread request body so the keep-alive connection stays in sync."""
        try:
            n = int(self.headers.get("Content-Length") or 0)
            while n > 0:
                n -= len(self.rfile.read(min(n, 1 << 16)))
        except Exception:
            pass

    def body(self, limit=20_000):
        n = int(self.headers.get("Content-Length") or 0)
        if n > limit or n <= 0:
            if n > limit:
                self.drain()
            return {}
        try:
            return json.loads(self.rfile.read(n) or b"{}")
        except Exception:
            return {}

    def body_to_file(self, limit):
        """Streams the raw request body into a temp file. Returns its path, or None when too big / empty."""
        n = int(self.headers.get("Content-Length") or 0)
        if n <= 0 or n > limit:
            self.drain()
            return None
        DATA.mkdir(parents=True, exist_ok=True)
        fd, path = tempfile.mkstemp(dir=str(DATA), prefix="upload-")
        try:
            with os.fdopen(fd, "wb") as f:
                left = n
                while left > 0:
                    chunk = self.rfile.read(min(left, 1 << 20))
                    if not chunk:
                        raise IOError("upload interrupted")
                    f.write(chunk)
                    left -= len(chunk)
        except Exception:
            try:
                os.unlink(path)
            except OSError:
                pass
            raise
        return path

    def base_url(self):
        if PUBLIC_URL:
            return PUBLIC_URL
        proto = (self.headers.get("X-Forwarded-Proto") if self.trust_proxy else None) or ("https" if SSL_ON else "http")
        return f"{proto}://{self.headers.get('Host', 'localhost')}"

    # ---- user session
    def session(self, conn):
        """Returns (user, token_hash) or sends the error and returns None."""
        h = self.headers.get("Authorization", "")
        if not h.startswith("Bearer "):
            self.send(401, {"error": "invalid"}); return None
        th = tok_hash(h[7:])
        row = conn.execute("SELECT u.*, t.device_id AS did FROM tokens t JOIN users u ON u.id=t.user_id WHERE t.token_hash=?", (th,)).fetchone()
        if not row:
            self.send(401, {"error": "invalid"}); return None
        if not row["enabled"]:
            self.send(403, {"error": "disabled"}); return None
        if row["expires"] and row["expires"] < time.time():
            self.send(403, {"error": "expired"}); return None
        now = int(time.time())
        conn.execute("UPDATE tokens SET last_seen=? WHERE token_hash=?", (now, th))
        conn.execute("UPDATE devices SET last_seen=? WHERE user_id=? AND device_id=?", (now, row["id"], row["did"]))
        conn.commit()
        return row, th

    # ---- routing
    def dispatch(self, method):
        u = urlparse(self.path)
        path, query = u.path, parse_qs(u.query)
        try:
            if path.startswith("/admin/api/"):
                return self.admin_api(method, path, query)
            if method == "GET" or method == "HEAD":
                if path == "/health":
                    return self.send(200, {"ok": True})
                if path in ("/admin", "/admin/"):
                    return self.send_raw(200, PANEL_HTML.encode("utf-8"), "text/html; charset=utf-8", {
                        "Content-Security-Policy": "default-src 'none'; script-src 'unsafe-inline'; style-src 'unsafe-inline'; connect-src 'self'; img-src data:; base-uri 'none'; form-action 'none'",
                        "X-Frame-Options": "DENY", "Referrer-Policy": "no-referrer"})
                if path == "/update.json":
                    return self.update_json()
                if path.startswith("/dl/"):
                    return self.download(path[4:])
                if path in ("/api/ping", "/api/config"):
                    conn = db()
                    try:
                        s = self.session(conn)
                        if not s:
                            return
                        if path == "/api/ping":
                            return self.send(200, {"ok": True, "expiresAt": s[0]["expires"]})
                        return self.config(s[0]["expires"])
                    finally:
                        conn.close()
                return self.send(404, {"error": "not_found"})
            if method == "POST":
                if path == "/api/login":
                    conn = db()
                    try:
                        return self.login(conn)
                    finally:
                        conn.close()
                if path == "/api/logout":
                    conn = db()
                    try:
                        s = self.session(conn)
                        if s:
                            conn.execute("DELETE FROM tokens WHERE token_hash=?", (s[1],)); conn.commit()
                            self.send(200, {"ok": True})
                        return
                    finally:
                        conn.close()
            self.drain()
            return self.send(404, {"error": "not_found"})
        except (BrokenPipeError, ConnectionResetError):
            pass
        except Exception as e:     # never show a stack trace to the client
            sys.stderr.write("error: %r\n" % (e,))
            try:
                self.send(500, {"error": "server_error"})
            except Exception:
                pass

    def do_GET(self): self.dispatch("GET")
    def do_HEAD(self): self.dispatch("HEAD")
    def do_POST(self): self.dispatch("POST")
    def do_PUT(self): self.dispatch("PUT")
    def do_DELETE(self): self.dispatch("DELETE")

    # ---- public: config for signed-in users (ETag + gzip)
    def config(self, expires):
        body, etag = config_body(expires)
        if self.headers.get("If-None-Match", "").strip() == etag:
            return self.send_raw(304, b"", extra={"ETag": etag})
        extra = {"ETag": etag}
        if "gzip" in self.headers.get("Accept-Encoding", ""):
            with LOCK:
                gz = _GZ.get(etag)
                if gz is None:
                    if len(_GZ) > 32:
                        _GZ.clear()
                    gz = _GZ[etag] = gzip.compress(body, 6)
            extra["Content-Encoding"] = "gzip"
            return self.send_raw(200, gz, extra=extra)
        return self.send_raw(200, body, extra=extra)

    # ---- public: app update feed and APK download
    def update_json(self):
        m = read_release()
        if not m:
            return self.send(404, {"error": "no_release"})
        feed = {"versionCode": m["versionCode"], "versionName": m["versionName"], "apkUrl": f"{self.base_url()}/dl/{m['file']}",
                "sha256": m["sha256"], "notes": m.get("notes", ""), "mandatory": bool(m.get("mandatory"))}
        self.send_raw(200, json.dumps(feed, ensure_ascii=False).encode("utf-8"), extra={"Cache-Control": "no-cache"})

    def download(self, name):
        name = unquote(name)
        f = UPDATE_DIR / name
        if not APK_NAME.match(name) or not f.is_file():
            return self.send(404, {"error": "not_found"})
        size = f.stat().st_size
        start, end, code = 0, size - 1, 200
        rng = self.headers.get("Range", "")
        m = re.match(r"^bytes=(\d*)-(\d*)$", rng)
        if m and (m.group(1) or m.group(2)):
            if m.group(1):
                start = int(m.group(1))
                end = int(m.group(2)) if m.group(2) else size - 1
            else:
                start = max(0, size - int(m.group(2)))
            if start >= size or end < start:
                return self.send_raw(416, b"", extra={"Content-Range": f"bytes */{size}"})
            end = min(end, size - 1)
            code = 206
        length = end - start + 1
        self.send_response(code)
        self.send_header("Content-Type", "application/vnd.android.package-archive")
        self.send_header("Content-Length", str(length))
        self.send_header("Accept-Ranges", "bytes")
        self.send_header("Content-Disposition", f'attachment; filename="{name}"')
        if code == 206:
            self.send_header("Content-Range", f"bytes {start}-{end}/{size}")
        self.end_headers()
        if self.command == "HEAD":
            return
        with open(f, "rb") as fh:
            fh.seek(start)
            left = length
            while left > 0:
                chunk = fh.read(min(left, 1 << 20))
                if not chunk:
                    break
                self.wfile.write(chunk)
                left -= len(chunk)

    # ---- public: login
    def login(self, conn):
        b = self.body()
        username = str(b.get("username", "")).strip().lower()
        password = str(b.get("password", ""))
        device = str(b.get("deviceId", ""))[:80]
        model = str(b.get("model", ""))[:80]
        key = (self.ip(), username)
        now = time.time()
        FAILS[key] = [t for t in FAILS.get(key, []) if now - t < WINDOW]
        if len(FAILS[key]) >= MAX_FAILS:
            return self.send(429, {"error": "rate"})
        u = conn.execute("SELECT * FROM users WHERE username=?", (username,)).fetchone()
        if not u or not device or not check_pw(password, u["pw"]):
            FAILS[key].append(now)
            return self.send(401, {"error": "invalid"})
        if not u["enabled"]:
            return self.send(403, {"error": "disabled"})
        if u["expires"] and u["expires"] < now:
            return self.send(403, {"error": "expired"})
        known = conn.execute("SELECT 1 FROM devices WHERE user_id=? AND device_id=?", (u["id"], device)).fetchone()
        if not known:
            used = conn.execute("SELECT COUNT(*) FROM devices WHERE user_id=?", (u["id"],)).fetchone()[0]
            if u["max_devices"] and used >= u["max_devices"]:
                return self.send(403, {"error": "device_limit"})
            conn.execute("INSERT INTO devices VALUES(?,?,?,?,?)", (u["id"], device, model, int(now), int(now)))
        token = secrets.token_urlsafe(32)
        conn.execute("INSERT INTO tokens VALUES(?,?,?,?,?)", (tok_hash(token), u["id"], device, int(now), int(now)))
        conn.commit()
        FAILS.pop(key, None)
        self.send(200, {"token": token, "username": u["username"], "expiresAt": u["expires"]})

    # ---- admin API
    def admin_api(self, method, path, query):
        key = (self.ip(), "__admin__")
        now = time.time()
        FAILS[key] = [t for t in FAILS.get(key, []) if now - t < WINDOW]
        if len(FAILS[key]) >= MAX_FAILS:
            self.drain()
            return self.send(429, {"error": "rate"})
        h = self.headers.get("Authorization", "")
        if not h.startswith("Bearer ") or not admin_token_ok(h[7:].strip()):
            FAILS[key].append(now)
            self.drain()
            return self.send(401, {"error": "admin_auth"})
        FAILS.pop(key, None)
        parts = [unquote(p) for p in path.split("/")[3:]]      # after /admin/api/
        conn = db()
        try:
            return self.admin_route(conn, method, parts, query)
        finally:
            conn.close()

    def admin_route(self, conn, method, parts, query):
        p0 = parts[0] if parts else ""
        if method == "GET" and p0 == "state":
            return self.send(200, self.state(conn))
        if method == "GET" and p0 == "ovpn" and len(parts) == 2:
            f = OVPN_DIR / (safe_stem(remove_suffix(parts[1], ".ovpn")) + ".ovpn")
            if not f.exists():
                return self.send(404, {"error": "not_found"})
            return self.send_raw(200, f.read_bytes(), "text/plain; charset=utf-8")

        if method == "POST" and p0 == "ovpn" and len(parts) == 1:
            b = self.body(25_000_000)
            files = {}
            for it in b.get("files", [])[:6000]:
                try:
                    files[str(it["name"])] = str(it["text"]).encode("utf-8")
                except Exception:
                    pass
            return self.ovpn_result(files)
        if method == "POST" and p0 == "ovpn-zip":
            tmp = self.body_to_file(80 * 1024 * 1024)
            if not tmp:
                return self.send(413, {"error": "too_big_or_empty"})
            try:
                try:
                    files = read_zip(tmp)
                except (zipfile.BadZipFile, ValueError) as e:
                    return self.send(400, {"error": str(e) or "bad_zip"})
            finally:
                try:
                    os.unlink(tmp)
                except OSError:
                    pass
            return self.ovpn_result(files)
        if method == "POST" and p0 == "ovpn-delete":
            b = self.body(2_000_000)
            names = b.get("names", [])
            if b.get("all") is True and b.get("confirm") == "DELETE-ALL":
                names = [f.stem for f in ovpn_paths()]
            return self.send(200, {"deleted": delete_ovpn(names)})
        if method == "DELETE" and p0 == "ovpn" and len(parts) == 2:
            return self.send(200, {"deleted": delete_ovpn([parts[1]])})

        if method == "PUT" and p0 == "config":
            return self.save_config(self.body(5_000_000))

        if p0 == "users":
            return self.users_route(conn, method, parts, query)

        if method == "POST" and p0 == "release":
            def q(k, d=""):
                return (query.get(k) or [d])[0]
            try:
                code = int(q("versionCode"))
            except ValueError:
                self.drain()
                return self.send(400, {"error": "versionCode must be a number"})
            tmp = self.body_to_file(MAX_APK)
            if not tmp:
                return self.send(413, {"error": "too_big_or_empty"})
            try:
                meta = save_release(tmp, code, q("versionName"), q("notes"), q("mandatory") in ("1", "true"), q("force") in ("1", "true"))
            except ValueError as e:
                try:
                    os.unlink(tmp)
                except OSError:
                    pass
                return self.send(400, {"error": str(e)})
            return self.send(200, {"ok": True, "release": meta, "feed": self.base_url() + "/update.json"})
        if method == "DELETE" and p0 == "release":
            with LOCK:
                if UPDATE_DIR.exists():
                    for f in UPDATE_DIR.glob("*"):
                        f.unlink()
            return self.send(200, {"ok": True})
        self.drain()
        return self.send(404, {"error": "not_found"})

    def ovpn_result(self, files):
        profiles, report = prepare_ovpn(files)
        try:
            names = save_ovpn(profiles)
        except ValueError as e:
            return self.send(400, {"error": str(e)})
        return self.send(200, {"added": len(names), "names": names[:50], "skipped": report[:200], "skippedCount": len(report)})

    def state(self, conn):
        cfg = read_cfg()
        ov = []
        for f in ovpn_paths():
            try:
                head = f.read_text(encoding="utf-8", errors="replace")
            except OSError:
                continue
            m = re.search(r"(?m)^\s*remote\s+(\S+)(?:\s+(\d+))?", head)
            ov.append({"name": f.stem, "cc": cc_of(f.stem), "size": f.stat().st_size, "remote": (m.group(1) + (":" + m.group(2) if m.group(2) else "")) if m else ""})
        v = cfg["vpn"]
        return {"users": users_list(conn), "ovpn": ov, "servers": cfg["servers"], "message": cfg.get("message", ""),
                "vpn": {"user": v.get("user", ""), "remoteId": v.get("remoteId", ""), "hasPass": bool(v.get("pass"))},
                "release": read_release(), "publicUrl": self.base_url(), "feed": self.base_url() + "/update.json", "now": int(time.time())}

    def save_config(self, b):
        with LOCK:
            cfg = read_cfg()
            if isinstance(b.get("vpn"), dict):
                v = cfg["vpn"]
                for k in ("user", "remoteId"):
                    if k in b["vpn"]:
                        v[k] = str(b["vpn"][k]).strip()
                if b["vpn"].get("pass"):
                    v["pass"] = str(b["vpn"]["pass"])
            if isinstance(b.get("servers"), list):
                servers, seen = [], set()
                for s in b["servers"][:5000]:
                    try:
                        addr = str(s["address"]).strip().replace(" ", "")
                        cc = str(s.get("cc", "")).strip().upper()[:2]
                    except Exception:
                        continue
                    if addr and addr not in seen and re.match(r"^[A-Za-z0-9._:-]{1,253}$", addr):
                        seen.add(addr)
                        servers.append({"cc": cc, "address": addr})
                cfg["servers"] = servers
            if "message" in b:
                cfg["message"] = str(b["message"])[:500]
            write_cfg(cfg)
        return self.send(200, {"ok": True, "servers": len(cfg["servers"])})

    def users_route(self, conn, method, parts, query):
        if method == "POST" and len(parts) == 1:
            b = self.body(10_000)
            name = str(b.get("username", "")).strip().lower()
            if not re.match(r"^[a-z0-9._-]{2,32}$", name):
                return self.send(400, {"error": "username: 2-32 letters, digits . _ -"})
            pw = str(b.get("password") or "") or new_password()
            days = int(b.get("days") or 0)
            exp = int(time.time() + days * DAY) if days > 0 else 0
            try:
                conn.execute("INSERT INTO users(username,pw,expires,max_devices,created,note) VALUES(?,?,?,?,?,?)",
                             (name, hash_pw(pw), exp, int(b.get("devices", 2)), int(time.time()), str(b.get("note") or "")[:200]))
                conn.commit()
            except sqlite3.IntegrityError:
                return self.send(409, {"error": "that username already exists"})
            return self.send(200, {"username": name, "password": pw, "expires": exp})
        if len(parts) >= 2:
            u = user_row(conn, parts[1])
            if not u:
                self.drain()
                return self.send(404, {"error": "no such user"})
            if method == "DELETE" and len(parts) == 2:
                for t in ("tokens", "devices"):
                    conn.execute(f"DELETE FROM {t} WHERE user_id=?", (u["id"],))
                conn.execute("DELETE FROM users WHERE id=?", (u["id"],)); conn.commit()
                return self.send(200, {"ok": True})
            if method == "POST" and len(parts) == 3:
                act, b = parts[2], self.body(10_000)
                if act in ("enable", "disable"):
                    conn.execute("UPDATE users SET enabled=? WHERE id=?", (1 if act == "enable" else 0, u["id"]))
                    if act == "disable":
                        conn.execute("DELETE FROM tokens WHERE user_id=?", (u["id"],))
                elif act == "extend":
                    start = max(int(time.time()), u["expires"] or 0)
                    conn.execute("UPDATE users SET expires=? WHERE id=?", (start + int(b.get("days", 30)) * DAY, u["id"]))
                elif act == "set":
                    if "devices" in b:
                        conn.execute("UPDATE users SET max_devices=? WHERE id=?", (int(b["devices"]), u["id"]))
                    if "note" in b:
                        conn.execute("UPDATE users SET note=? WHERE id=?", (str(b["note"])[:200], u["id"]))
                    if "expires" in b:
                        conn.execute("UPDATE users SET expires=? WHERE id=?", (int(b["expires"]), u["id"]))
                elif act == "passwd":
                    pw = str(b.get("password") or "") or new_password()
                    conn.execute("UPDATE users SET pw=? WHERE id=?", (hash_pw(pw), u["id"]))
                    conn.execute("DELETE FROM tokens WHERE user_id=?", (u["id"],))
                    conn.commit()
                    return self.send(200, {"ok": True, "password": pw})
                elif act == "kick":
                    conn.execute("DELETE FROM tokens WHERE user_id=?", (u["id"],))
                elif act == "reset-devices":
                    conn.execute("DELETE FROM devices WHERE user_id=?", (u["id"],)); conn.execute("DELETE FROM tokens WHERE user_id=?", (u["id"],))
                else:
                    return self.send(404, {"error": "unknown action"})
                conn.commit()
                return self.send(200, {"ok": True})
        self.drain()
        return self.send(404, {"error": "not_found"})


def serve(a):
    global PUBLIC_URL, SSL_ON
    Handler.trust_proxy = a.trust_proxy
    if a.public_url:
        PUBLIC_URL = a.public_url.rstrip("/")
    httpd = ThreadingHTTPServer((a.host, a.port), Handler)
    httpd.daemon_threads = True
    if a.cert and a.key:
        ctx = ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER)
        ctx.load_cert_chain(a.cert, a.key)
        httpd.socket = ctx.wrap_socket(httpd.socket, server_side=True)
        SSL_ON = True
    print(f"MYLO server on {'https' if SSL_ON else 'http'}://{a.host}:{a.port}   admin panel: /admin")
    if not ADMIN_FILE.exists() and not os.environ.get("MYLO_ADMIN_TOKEN"):
        print("No admin token yet. Create it with:  python3 mylo_server.py admin-token")
    try:
        httpd.serve_forever()
    except KeyboardInterrupt:
        pass


# ---------------------------------------------------------------- command line
def fmt_date(t):
    return "never" if not t else time.strftime("%Y-%m-%d", time.localtime(t))


def get_user(conn, name):
    u = conn.execute("SELECT * FROM users WHERE username=?", (name.lower(),)).fetchone()
    if not u:
        sys.exit(f"no such user: {name}")
    return u


def ovpn_cmd(a):
    OVPN_DIR.mkdir(parents=True, exist_ok=True)
    if a.cmd == "ovpn-import":
        profiles, report = prepare_ovpn(collect_inputs(a.paths))
        names = save_ovpn(profiles)
        print(f"added {len(names)} profile(s)")
        for r in report:
            print("skipped:", r)
    elif a.cmd == "add-ovpn":      # kept for older notes: one file
        profiles, report = prepare_ovpn(collect_inputs([a.file]))
        if a.name and len(profiles) == 1:
            profiles = {safe_stem(a.name): list(profiles.values())[0]}
        if not profiles:
            sys.exit("not added:\n  " + "\n  ".join(report))
        save_ovpn(profiles)
        print("added", ", ".join(profiles))
    elif a.cmd == "list-ovpn":
        files = ovpn_paths()
        for f in files:
            print(f.name, f"{f.stat().st_size} bytes")
        if not files:
            print("no OpenVPN profiles yet")
    elif a.cmd == "remove-ovpn":
        if not delete_ovpn([a.name]):
            sys.exit("no such profile: " + a.name)
        print("removed", a.name)


def admin(a):
    conn = db()
    if a.cmd == "add":
        pw = a.password or new_password()
        exp = int(time.time() + a.days * DAY) if a.days else 0
        try:
            conn.execute("INSERT INTO users(username,pw,expires,max_devices,created,note) VALUES(?,?,?,?,?,?)",
                         (a.username.lower(), hash_pw(pw), exp, a.devices, int(time.time()), a.note or ""))
        except sqlite3.IntegrityError:
            sys.exit("that username already exists")
        conn.commit()
        print(f"username: {a.username.lower()}\npassword: {pw}\nexpires:  {fmt_date(exp)}\ndevices:  {a.devices or 'unlimited'}")
    elif a.cmd == "list":
        print(f"{'user':<16}{'on':<5}{'expires':<12}{'devices':<10}{'last seen':<18}note")
        for u in users_list(conn):
            seen = time.strftime("%Y-%m-%d %H:%M", time.localtime(u["lastSeen"])) if u["lastSeen"] else "-"
            print(f"{u['username']:<16}{'yes' if u['enabled'] else 'NO':<5}{fmt_date(u['expires']):<12}{str(u['devices']) + '/' + str(u['maxDevices'] or 'inf'):<10}{seen:<18}{u['note']}")
    elif a.cmd in ("disable", "enable"):
        u = get_user(conn, a.username)
        conn.execute("UPDATE users SET enabled=? WHERE id=?", (1 if a.cmd == "enable" else 0, u["id"]))
        if a.cmd == "disable":
            conn.execute("DELETE FROM tokens WHERE user_id=?", (u["id"],))
        conn.commit(); print(a.cmd + "d", u["username"])
    elif a.cmd == "delete":
        u = get_user(conn, a.username)
        for t in ("tokens", "devices"):
            conn.execute(f"DELETE FROM {t} WHERE user_id=?", (u["id"],))
        conn.execute("DELETE FROM users WHERE id=?", (u["id"],)); conn.commit(); print("deleted", u["username"])
    elif a.cmd == "passwd":
        u = get_user(conn, a.username)
        pw = a.password or new_password()
        conn.execute("UPDATE users SET pw=? WHERE id=?", (hash_pw(pw), u["id"]))
        conn.execute("DELETE FROM tokens WHERE user_id=?", (u["id"],)); conn.commit()
        print(f"new password for {u['username']}: {pw}")
    elif a.cmd == "extend":
        u = get_user(conn, a.username)
        start = max(int(time.time()), u["expires"] or 0)
        conn.execute("UPDATE users SET expires=? WHERE id=?", (start + a.days * DAY, u["id"])); conn.commit()
        print(f"{u['username']} now expires {fmt_date(start + a.days * DAY)}")
    elif a.cmd == "devices":
        u = get_user(conn, a.username)
        for d in conn.execute("SELECT * FROM devices WHERE user_id=?", (u["id"],)):
            print(d["device_id"][:12], d["model"], "last seen", time.strftime("%Y-%m-%d %H:%M", time.localtime(d["last_seen"])))
    elif a.cmd == "reset-devices":
        u = get_user(conn, a.username)
        conn.execute("DELETE FROM devices WHERE user_id=?", (u["id"],)); conn.execute("DELETE FROM tokens WHERE user_id=?", (u["id"],)); conn.commit()
        print("device slots freed for", u["username"])
    elif a.cmd == "kick":
        u = get_user(conn, a.username)
        conn.execute("DELETE FROM tokens WHERE user_id=?", (u["id"],)); conn.commit(); print("logged out everywhere:", u["username"])


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    sub = ap.add_subparsers(dest="cmd", required=True)
    s = sub.add_parser("serve")
    s.add_argument("--host", default="0.0.0.0"); s.add_argument("--port", type=int, default=8080)
    s.add_argument("--cert"); s.add_argument("--key")
    s.add_argument("--trust-proxy", action="store_true", help="read the client IP / scheme from X-Forwarded-* (behind Caddy/nginx)")
    s.add_argument("--public-url", help="https://api.your-domain.com (used in update.json; recommended)")
    t = sub.add_parser("admin-token"); t.add_argument("--new", action="store_true", help="replace the existing token")
    p = sub.add_parser("add"); p.add_argument("username"); p.add_argument("--password")
    p.add_argument("--days", type=int, default=0); p.add_argument("--devices", type=int, default=2); p.add_argument("--note")
    sub.add_parser("list")
    for c in ("disable", "enable", "delete", "devices", "reset-devices", "kick"):
        sub.add_parser(c).add_argument("username")
    p = sub.add_parser("ovpn-import"); p.add_argument("paths", nargs="+")
    p = sub.add_parser("add-ovpn"); p.add_argument("file"); p.add_argument("--name")
    sub.add_parser("list-ovpn")
    sub.add_parser("remove-ovpn").add_argument("name")
    p = sub.add_parser("passwd"); p.add_argument("username"); p.add_argument("--password")
    p = sub.add_parser("extend"); p.add_argument("username"); p.add_argument("--days", type=int, required=True)
    a = ap.parse_args()
    if a.cmd == "serve":
        serve(a)
    elif a.cmd == "admin-token":
        if ADMIN_FILE.exists() and not a.new:
            sys.exit("An admin token already exists (it is stored hashed and cannot be shown again). Use --new to replace it.")
        print("Admin token (save it now, it is not stored in readable form):\n  " + make_admin_token())
    elif a.cmd in ("ovpn-import", "add-ovpn", "list-ovpn", "remove-ovpn"):
        ovpn_cmd(a)
    else:
        admin(a)


# ---------------------------------------------------------------- web panel (single page)
PANEL_HTML = r"""<!doctype html>
<html lang="fa" dir="rtl"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>MYLO - پنل مدیریت</title>
<style>
:root{--bg:#0f1720;--card:#17212c;--ink:#e8eef4;--mut:#8aa0b3;--teal:#08cad7;--red:#ff5d73;--line:#263444;--ok:#3ddc97}
*{box-sizing:border-box}body{margin:0;font:15px system-ui,Tahoma,sans-serif;background:var(--bg);color:var(--ink)}
header{display:flex;gap:12px;align-items:center;padding:12px 18px;border-bottom:1px solid var(--line);flex-wrap:wrap}
header b{font-size:18px;color:var(--teal)}nav{display:flex;gap:6px;flex-wrap:wrap;flex:1}
nav button,.btn{background:var(--card);color:var(--ink);border:1px solid var(--line);padding:8px 14px;border-radius:10px;cursor:pointer;font:inherit}
nav button.on{border-color:var(--teal);color:var(--teal)}.btn.pri{background:var(--teal);color:#032;border-color:var(--teal)}
.btn.bad{color:var(--red);border-color:var(--red)}.btn:disabled{opacity:.5;cursor:default}
main{max-width:1100px;margin:0 auto;padding:16px}.card{background:var(--card);border:1px solid var(--line);border-radius:14px;padding:16px;margin-bottom:16px}
h2{margin:0 0 12px;font-size:17px}input,textarea,select{background:#0c141c;color:var(--ink);border:1px solid var(--line);border-radius:8px;padding:8px 10px;font:inherit;max-width:100%}
textarea{width:100%;min-height:160px;font-family:ui-monospace,Consolas,monospace;font-size:13px;direction:ltr}
.row{display:flex;gap:10px;flex-wrap:wrap;align-items:center;margin-bottom:10px}label{color:var(--mut);font-size:13px}
table{width:100%;border-collapse:collapse}th,td{padding:7px 8px;border-bottom:1px solid var(--line);text-align:right;font-size:14px;vertical-align:middle}
th{color:var(--mut);font-weight:normal}td.l{direction:ltr;text-align:left}.mut{color:var(--mut)}.ok{color:var(--ok)}.bad-t{color:var(--red)}
.drop{border:2px dashed var(--line);border-radius:12px;padding:22px;text-align:center;color:var(--mut)}.drop.over{border-color:var(--teal);color:var(--teal)}
pre{white-space:pre-wrap;direction:ltr;text-align:left;background:#0c141c;border-radius:8px;padding:10px;max-height:240px;overflow:auto;font-size:12px}
#login{max-width:420px;margin:12vh auto}#toast{position:fixed;bottom:18px;left:50%;transform:translateX(-50%);background:#000;color:#fff;padding:10px 18px;border-radius:10px;display:none;max-width:90vw}
.bar{height:8px;background:#0c141c;border-radius:6px;overflow:hidden}.bar i{display:block;height:100%;background:var(--teal);width:0}
</style></head><body>
<div id="login" class="card"><h2>MYLO - ورود مدیر</h2>
<p class="mut">توکن مدیر را وارد کنید (ساخت: <code dir="ltr">python3 mylo_server.py admin-token</code>)</p>
<div class="row"><input id="tok" type="password" style="flex:1" dir="ltr" placeholder="mylo_..."><button class="btn pri" id="go">ورود</button></div><div id="lerr" class="bad-t"></div></div>
<div id="app" style="display:none">
<header><b>MYLO</b><nav id="nav"></nav><button class="btn" id="out">خروج</button></header>
<main id="main"></main></div><div id="toast"></div>
<script>
"use strict";
const $=s=>document.querySelector(s);
let TOKEN=sessionStorage.getItem('mylo_t')||'',S=null,TAB='ovpn',SEL=new Set(),Q='';
function el(t,a,...c){const e=document.createElement(t);for(const k in (a||{})){if(k==='on')for(const ev in a.on)e.addEventListener(ev,a.on[ev]);else if(k==='cls')e.className=a[k];else if(k==='val')e.value=a[k];else if(k==='chk')e.checked=a[k];else e.setAttribute(k,a[k])}
 for(const x of c.flat()){if(x==null||x===false)continue;e.append(x.nodeType?x:document.createTextNode(String(x)))}return e}
function toast(m,bad){const t=$('#toast');t.textContent=m;t.style.background=bad?'#7a1626':'#000';t.style.display='block';clearTimeout(toast.t);toast.t=setTimeout(()=>t.style.display='none',4500)}
async function api(method,path,body,raw){const o={method,headers:{'Authorization':'Bearer '+TOKEN}};
 if(body!==undefined){if(raw){o.body=body}else{o.headers['Content-Type']='application/json';o.body=JSON.stringify(body)}}
 const r=await fetch('/admin/api/'+path,o);let j={};try{j=await r.json()}catch(e){}
 if(r.status===401){logout();throw new Error('توکن نادرست است')}
 if(!r.ok)throw new Error(j.error||('HTTP '+r.status));return j}
function logout(){TOKEN='';sessionStorage.removeItem('mylo_t');$('#app').style.display='none';$('#login').style.display='block'}
async function load(){S=await api('GET','state');render()}
function date(t){return t?new Date(t*1000).toISOString().slice(0,10):'بدون انقضا'}
function ago(t){if(!t)return '-';const s=S.now-t;return s<90?'الان':s<5400?Math.round(s/60)+' دقیقه پیش':s<172800?Math.round(s/3600)+' ساعت پیش':Math.round(s/86400)+' روز پیش'}
const TABS={ovpn:'فایل‌های OpenVPN',users:'کاربران',servers:'سرورها و تنظیمات',update:'آپدیت برنامه'};
function render(){const nav=$('#nav');nav.replaceChildren(...Object.keys(TABS).map(k=>el('button',{cls:k===TAB?'on':'',on:{click:()=>{TAB=k;render()}}},TABS[k])));
 const m=$('#main');m.replaceChildren(({ovpn:vOvpn,users:vUsers,servers:vServers,update:vUpdate})[TAB]())}
// ---------- OpenVPN
function vOvpn(){const log=el('pre',{style:'display:none'});const prog=el('div',{cls:'bar',style:'display:none'},el('i'));
 const inp=el('input',{type:'file',multiple:'',accept:'.ovpn,.conf,.zip,.crt,.pem,.key,.txt,.cer'});
 async function send(files){if(!files.length)return;log.style.display='block';prog.style.display='block';log.textContent='';let added=0,skipped=[];
  const zips=files.filter(f=>/\.zip$/i.test(f.name)),rest=files.filter(f=>!/\.zip$/i.test(f.name));
  try{for(const z of zips){const r=await api('POST','ovpn-zip',z,true);added+=r.added;skipped.push(...r.skipped)}
   const CH=150;for(let i=0;i<rest.length;i+=CH){const part=rest.slice(i,i+CH);const items=[];for(const f of part)items.push({name:f.webkitRelativePath||f.name,text:await f.text()});
    const r=await api('POST','ovpn',{files:items});added+=r.added;skipped.push(...r.skipped);prog.firstChild.style.width=Math.round((i+part.length)/rest.length*100)+'%'}
   log.textContent='اضافه شد: '+added+'\n'+(skipped.length?'رد شد ('+skipped.length+'):\n'+skipped.join('\n'):'');toast('اضافه شد: '+added);await load()}
  catch(e){log.textContent=e.message;toast(e.message,1)}prog.style.display='none'}
 const drop=el('div',{cls:'drop',on:{dragover:e=>{e.preventDefault();drop.classList.add('over')},dragleave:()=>drop.classList.remove('over'),drop:e=>{e.preventDefault();drop.classList.remove('over');send([...e.dataTransfer.files])}}},'فایل‌های .ovpn یا یک فایل .zip را اینجا بکشید یا انتخاب کنید',el('br'),el('br'),inp,
  el('div',{cls:'mut',style:'margin-top:8px;font-size:12px'},'اگر فایل به ca.crt / ta.key اشاره می‌کند، آن‌ها را هم با همین انتخاب کنید (یا zip کنید)؛ داخل فایل قرار داده می‌شوند.'));
 inp.addEventListener('change',()=>send([...inp.files]));
 const list=S.ovpn.filter(o=>!Q||(o.name+o.remote).toLowerCase().includes(Q.toLowerCase()));
 const all=el('input',{type:'checkbox',on:{change:e=>{list.forEach(o=>e.target.checked?SEL.add(o.name):SEL.delete(o.name));render()}}});
 const tb=el('table',{},el('tr',{},el('th',{},all),el('th',{},'نام'),el('th',{},'کشور'),el('th',{},'سرور'),el('th',{},'حجم'),el('th')),
  list.slice(0,600).map(o=>el('tr',{},el('td',{},el('input',{type:'checkbox',chk:SEL.has(o.name),on:{change:e=>{e.target.checked?SEL.add(o.name):SEL.delete(o.name);render()}}})),el('td',{cls:'l'},o.name),el('td',{},o.cc||'-'),el('td',{cls:'l mut'},o.remote),el('td',{cls:'mut'},Math.round(o.size/102.4)/10+' KB'),
   el('td',{},el('button',{cls:'btn bad',on:{click:async()=>{if(confirm(o.name+' حذف شود؟')){await api('DELETE','ovpn/'+encodeURIComponent(o.name));SEL.delete(o.name);load()}}}},'حذف')))));
 return el('div',{},el('div',{cls:'card'},el('h2',{},'افزودن گروهی'),drop,prog,log),
  el('div',{cls:'card'},el('h2',{},'پروفایل‌ها ('+S.ovpn.length+')'),el('div',{cls:'row'},el('input',{placeholder:'جستجو',val:Q,on:{input:e=>{Q=e.target.value;const p=e.target.selectionStart;render();const n=$('#main input[placeholder="جستجو"]');if(n){n.focus();n.setSelectionRange(p,p)}}}}),
   el('button',{cls:'btn bad',disabled:SEL.size?null:'',on:{click:async()=>{if(!SEL.size||!confirm(SEL.size+' پروفایل حذف شود؟'))return;const r=await api('POST','ovpn-delete',{names:[...SEL]});SEL.clear();toast('حذف شد: '+r.deleted);load()}}},'حذف انتخاب‌شده‌ها ('+SEL.size+')'),
   el('button',{cls:'btn bad',on:{click:async()=>{if(prompt('برای حذف همه، DELETE-ALL را تایپ کنید')==='DELETE-ALL'){await api('POST','ovpn-delete',{all:true,confirm:'DELETE-ALL'});SEL.clear();load()}}}},'حذف همه')),
   list.length>600?el('div',{cls:'mut'},'۶۰۰ مورد اول نمایش داده می‌شود؛ جستجو کنید.'):null,tb))}
// ---------- users
function vUsers(){const u=el('input',{placeholder:'نام کاربری',dir:'ltr'}),d=el('input',{type:'number',placeholder:'روز (۰=بدون انقضا)',val:'30',style:'width:150px'}),n=el('input',{type:'number',val:'2',style:'width:70px'}),note=el('input',{placeholder:'یادداشت'}),res=el('div',{cls:'ok',style:'margin-top:8px;direction:ltr;text-align:left'});
 const add=el('div',{cls:'card'},el('h2',{},'کاربر جدید'),el('div',{cls:'row'},u,d,el('label',{},'دستگاه:'),n,note,el('button',{cls:'btn pri',on:{click:async()=>{try{const r=await api('POST','users',{username:u.value,days:+d.value,devices:+n.value,note:note.value});res.textContent='username: '+r.username+'   password: '+r.password+'   (فقط همین یک بار نمایش داده می‌شود)';load2()}catch(e){toast(e.message,1)}}}},'ساخت')),res);
 async function load2(){S=await api('GET','state');const t=$('#utable');if(t)t.replaceWith(table())}
 const act=(name,a,body,msg)=>async()=>{try{const r=await api('POST','users/'+encodeURIComponent(name)+'/'+a,body||{});if(r.password)prompt('رمز جدید (کپی کنید):',r.password);else toast(msg||'انجام شد');load2()}catch(e){toast(e.message,1)}};
 function table(){return el('table',{id:'utable'},el('tr',{},['کاربر','فعال','انقضا','دستگاه','آخرین ورود','یادداشت',''].map(x=>el('th',{},x))),S.users.map(x=>el('tr',{},el('td',{cls:'l'},x.username),el('td',{cls:x.enabled?'ok':'bad-t'},x.enabled?'بله':'خیر'),el('td',{cls:x.expires&&x.expires<S.now?'bad-t':''},date(x.expires)),el('td',{},x.devices+'/'+(x.maxDevices||'∞')),el('td',{cls:'mut'},ago(x.lastSeen)),el('td',{cls:'mut'},x.note),
  el('td',{},el('div',{cls:'row',style:'margin:0'},el('button',{cls:'btn',on:{click:act(x.username,'extend',{days:30},'۳۰ روز اضافه شد')}},'+۳۰ روز'),el('button',{cls:'btn',on:{click:act(x.username,x.enabled?'disable':'enable')}},x.enabled?'غیرفعال':'فعال'),el('button',{cls:'btn',on:{click:act(x.username,'passwd')}},'رمز جدید'),el('button',{cls:'btn',on:{click:act(x.username,'reset-devices',{},'دستگاه‌ها آزاد شد')}},'آزادسازی دستگاه'),el('button',{cls:'btn',on:{click:act(x.username,'kick',{},'خارج شد')}},'خروج'),
   el('button',{cls:'btn bad',on:{click:async()=>{if(confirm(x.username+' حذف شود؟')){await api('DELETE','users/'+encodeURIComponent(x.username));load2()}}}},'حذف'))))))}
 return el('div',{},add,el('div',{cls:'card'},el('h2',{},'کاربران ('+S.users.length+')'),table()))}
// ---------- servers + settings
function vServers(){const lines=S.servers.map(s=>(s.cc||'')+'|'+s.address).join('\n');const ta=el('textarea',{placeholder:'DE|de2-auto-ikev.ptoserver.com'},lines);ta.value=lines;
 const vu=el('input',{val:S.vpn.user,dir:'ltr'}),vp=el('input',{type:'password',placeholder:S.vpn.hasPass?'(بدون تغییر)':'رمز',dir:'ltr'}),vr=el('input',{val:S.vpn.remoteId,dir:'ltr'}),msg=el('input',{val:S.message,style:'width:100%'});
 return el('div',{},el('div',{cls:'card'},el('h2',{},'حساب VPN (IKEv2)'),el('div',{cls:'row'},el('label',{},'کاربر'),vu,el('label',{},'رمز'),vp,el('label',{},'Remote ID'),vr)),
  el('div',{cls:'card'},el('h2',{},'لیست سرورهای IKEv2 — هر خط: کشور|آدرس'),ta),
  el('div',{cls:'card'},el('h2',{},'پیام برای کاربران'),msg),
  el('button',{cls:'btn pri',on:{click:async()=>{const servers=ta.value.split('\n').map(l=>l.trim()).filter(Boolean).map(l=>{const i=l.indexOf('|');return i<0?{cc:'',address:l}:{cc:l.slice(0,i),address:l.slice(i+1)}});
   try{const r=await api('PUT','config',{servers,message:msg.value,vpn:{user:vu.value,pass:vp.value,remoteId:vr.value}});toast('ذخیره شد ('+r.servers+' سرور)');load()}catch(e){toast(e.message,1)}}}},'ذخیره'))}
// ---------- app update
function vUpdate(){const r=S.release;const f=el('input',{type:'file',accept:'.apk'}),vc=el('input',{type:'number',placeholder:'versionCode',style:'width:140px',val:r?String(r.versionCode+1):''}),vn=el('input',{placeholder:'versionName',dir:'ltr',style:'width:120px'}),notes=el('input',{placeholder:'توضیحات تغییرات',style:'flex:1;min-width:200px'}),man=el('input',{type:'checkbox'}),prog=el('div',{cls:'bar',style:'display:none'},el('i'));
 return el('div',{},el('div',{cls:'card'},el('h2',{},'نسخه‌ی منتشرشده'),r?el('div',{},el('div',{},'نسخه '+r.versionName+' (کد '+r.versionCode+') — '+Math.round(r.size/1048576*10)/10+' MB — '+date(r.published)),el('div',{cls:'mut',style:'direction:ltr;text-align:left'},'sha256 '+r.sha256),el('div',{cls:'mut'},r.notes||'')):el('div',{cls:'mut'},'هنوز نسخه‌ای روی این سرور منتشر نشده.'),
  el('div',{style:'margin-top:10px'},'آدرس Update Feed: ',el('code',{dir:'ltr'},S.feed))),
  el('div',{cls:'card'},el('h2',{},'انتشار نسخه‌ی جدید'),el('div',{cls:'row'},f),el('div',{cls:'row'},vc,vn,notes,el('label',{},el('span',{},'اجباری '),man)),prog,
  el('button',{cls:'btn pri',on:{click:()=>{const file=f.files[0];if(!file)return toast('فایل APK را انتخاب کنید',1);if(!vc.value||!vn.value)return toast('versionCode و versionName لازم است',1);
   const x=new XMLHttpRequest();x.open('POST','/admin/api/release?versionCode='+encodeURIComponent(vc.value)+'&versionName='+encodeURIComponent(vn.value)+'&notes='+encodeURIComponent(notes.value)+'&mandatory='+(man.checked?1:0));
   x.setRequestHeader('Authorization','Bearer '+TOKEN);prog.style.display='block';x.upload.onprogress=e=>{if(e.lengthComputable)prog.firstChild.style.width=Math.round(e.loaded/e.total*100)+'%'};
   x.onload=()=>{prog.style.display='none';let j={};try{j=JSON.parse(x.responseText)}catch(e){}if(x.status===200){toast('منتشر شد');load()}else toast(j.error||('HTTP '+x.status),1)};x.onerror=()=>{prog.style.display='none';toast('ارسال ناموفق بود',1)};x.send(file)}}},'آپلود و انتشار')))}
$('#go').onclick=async()=>{TOKEN=$('#tok').value.trim();try{await api('GET','state');sessionStorage.setItem('mylo_t',TOKEN);$('#login').style.display='none';$('#app').style.display='block';await load()}catch(e){$('#lerr').textContent=e.message}};
$('#tok').addEventListener('keydown',e=>{if(e.key==='Enter')$('#go').click()});$('#out').onclick=logout;
if(TOKEN){$('#tok').value=TOKEN;$('#go').click()}
</script></body></html>
"""


if __name__ == "__main__":
    main()

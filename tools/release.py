#!/usr/bin/env python3
"""
MYLO VPN - publish an update from your laptop.

  0) once:  python3 tools/release.py setup --github OWNER/REPO        (or  --base-url https://YOUR-DOMAIN/mylo)
            writes the update address into the app. Do it BEFORE the first release build.
<<<<<<< HEAD
  login: python3 tools/release.py auth-setup --url https://api.YOUR-DOMAIN   (see server/README.md)
  ovpn:  python3 tools/release.py ovpn-push DE-udp.ovpn US-tcp.ovpn --scp user@host:/opt/mylo/server/data/openvpn/
        checks the .ovpn files on your laptop and uploads them to the server (no app update needed)
=======
>>>>>>> origin/main
  1) python3 tools/release.py bump
        raises versionCode (+1) and versionName (last number +1) in app/build.gradle.kts
  2) Android Studio: Build > Select Build Variant > release, then Build > Build APK(s)
  3) python3 tools/release.py publish --base-url https://YOUR-DOMAIN/mylo --scp user@host:/var/www/mylo/ --notes "What's new"
        or  python3 tools/release.py publish --github OWNER/REPO --notes "What's new"
        (without --scp/--github it only prepares the ./dist folder for you to upload by hand)

Phones check update.json (the URL set in Defaults.UPDATE_URL), download the APK, verify its SHA-256 and let
Android install it. Android only accepts an APK signed with the same key as the installed app.
"""
import argparse, hashlib, json, pathlib, re, shutil, subprocess, sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
GRADLE = ROOT / "app" / "build.gradle.kts"
DEFAULT_APK = ROOT / "app" / "build" / "outputs" / "apk" / "release" / "app-release.apk"
DIST = ROOT / "dist"


def read_version():
    t = GRADLE.read_text()
    code = int(re.search(r"versionCode\s*=\s*(\d+)", t).group(1))
    name = re.search(r'versionName\s*=\s*"([^"]+)"', t).group(1)
    return code, name


def bump():
    code, name = read_version()
    parts = name.split(".")
    parts[-1] = str(int(parts[-1]) + 1)
    new_name = ".".join(parts)
    t = GRADLE.read_text()
    t = re.sub(r"(versionCode\s*=\s*)\d+", lambda m: m.group(1) + str(code + 1), t)
    t = re.sub(r'(versionName\s*=\s*")[^"]+(")', lambda m: m.group(1) + new_name + m.group(2), t)
    GRADLE.write_text(t)
    print(f"version {code} ({name})  ->  {code + 1} ({new_name})")
    print("Now build the release APK in Android Studio, then run: python3 tools/release.py publish ...")


DEFAULTS = ROOT / "app" / "src" / "main" / "java" / "com" / "ikev2split" / "app" / "data" / "Defaults.kt"


def setup(a):
    if a.github:
        feed = f"https://github.com/{a.github}/releases/download/update/update.json"
    elif a.base_url:
        feed = a.base_url.rstrip("/") + "/update.json"
    else:
        sys.exit("Give --github OWNER/REPO or --base-url https://YOUR-DOMAIN/mylo")
    t = DEFAULTS.read_text()
    new, n = re.subn(r'(const val UPDATE_URL = ")[^"]*(")', lambda m: m.group(1) + feed + m.group(2), t)
    if n != 1:
        sys.exit("could not find UPDATE_URL in Defaults.kt")
    DEFAULTS.write_text(new)
    print("update address set to:", feed)
    print("Now build the release APK, install it once on your phone (and send it once to others). After that updates come from the app.")


<<<<<<< HEAD
def auth_setup(a):
    """Turns the login on: exports the VPN account + server list to server/data/vpn.json and removes them from the app."""
    url = a.url.rstrip("/")
    if not url.startswith("https://"):
        sys.exit("The server address must start with https://")
    t = DEFAULTS.read_text()

    def const(name):
        m = re.search(r'const val %s = "((?:\\.|[^"\\])*)"' % name, t)
        return m.group(1) if m else ""

    def block(name):
        m = re.search(r'private const val %s = """(.*?)"""' % name, t, re.S)
        return m.group(1) if m else ""

    user = const("USER")
    pw = const("PASS").replace("\\$", "$")
    rid = const("RID") or "pointtoserver.com"
    servers = []
    for line in (block("LIST") + "\n" + block("LIST2")).splitlines():
        line = line.strip()
        if line:
            cc, h = line.split("|", 1)
            host = h if "." in h else h + ".ptoserver.com"
            if all(x["address"] != host for x in servers):
                servers.append({"cc": cc, "address": host})

    cfg = ROOT / "server" / "data" / "vpn.json"
    if user or servers:
        if cfg.exists() and "YOUR-VPN-USERNAME" not in cfg.read_text() and not a.force:
            sys.exit(f"{cfg} already has real data. Use --force to overwrite it.")
        cfg.parent.mkdir(parents=True, exist_ok=True)
        cfg.write_text(json.dumps({"vpn": {"user": user, "pass": pw, "remoteId": rid}, "servers": servers, "message": ""}, indent=2) + "\n")
        print(f"exported VPN account and {len(servers)} servers to {cfg}")
        t = re.sub(r'(const val USER = ")(?:\\.|[^"\\])*(")', r'\1\2', t)
        t = re.sub(r'(const val PASS = ")(?:\\.|[^"\\])*(")', r'\1\2', t)
        t = re.sub(r'(private const val LIST = """).*?(""")', r'\1\n\2', t, flags=re.S)
        t = re.sub(r'(private const val LIST2 = """).*?(""")', r'\1\n\2', t, flags=re.S)
    t = re.sub(r'(const val AUTH_URL = ")[^"]*(")', lambda m: m.group(1) + url + m.group(2), t)
    DEFAULTS.write_text(t)
    print("login is ON, server address:", url)
    print("The app no longer contains the VPN password or the server list; they come from your server after sign-in.")
    print("Next: read server/README.md, create users with  python3 server/mylo_server.py add NAME --days 30")


def ovpn_push(a):
    """Checks .ovpn files on the laptop (everything must be inside the file) and uploads them to the server."""
    sys.path.insert(0, str(ROOT / "server"))
    from mylo_server import check_ovpn, safe_stem
    DIST.mkdir(exist_ok=True)
    ok = []
    for f in a.files:
        src = pathlib.Path(f)
        errs, warns = check_ovpn(src.read_text(errors="replace"))
        for w in warns:
            print(f"{src.name}: note: {w}")
        if errs:
            print(f"{src.name}: SKIPPED\n    " + "\n    ".join(errs))
            continue
        dst = DIST / (safe_stem(src.stem) + ".ovpn")
        shutil.copyfile(src, dst)
        ok.append(dst)
    if not ok:
        sys.exit("nothing to upload")
    if a.scp:
        run(["scp", *map(str, ok), a.scp])
        print(f"uploaded {len(ok)} profile(s). Signed-in users get them in the app under Locations > OpenVPN.")
    else:
        print(f"checked {len(ok)} profile(s), copied to {DIST}. Put them in server/data/openvpn/ on the server (or pass --scp user@host:/path/).")


=======
>>>>>>> origin/main
def sha256(path):
    h = hashlib.sha256()
    with open(path, "rb") as f:
        for chunk in iter(lambda: f.read(1 << 20), b""):
            h.update(chunk)
    return h.hexdigest()


def run(cmd):
    print("$", " ".join(cmd))
    subprocess.run(cmd, check=True)


def publish(a):
    apk = pathlib.Path(a.apk) if a.apk else DEFAULT_APK
    if not apk.exists():
        sys.exit(f"APK not found: {apk}\nBuild the release variant first (Build > Build APK(s)).")
    code, name = read_version()
    apk_name = f"mylo-{name}.apk"

    if a.github:
        base = f"https://github.com/{a.github}/releases/download/v{name}"
    elif a.base_url:
        base = a.base_url.rstrip("/")
    else:
        sys.exit("Give --base-url https://YOUR-DOMAIN/mylo (where you will upload the files) or --github OWNER/REPO")

    DIST.mkdir(exist_ok=True)
    out_apk = DIST / apk_name
    shutil.copyfile(apk, out_apk)
    feed = {
        "versionCode": code,
        "versionName": name,
        "apkUrl": f"{base}/{apk_name}",
        "sha256": sha256(out_apk),
        "notes": a.notes or "",
        "mandatory": bool(a.mandatory),
    }
    out_json = DIST / "update.json"
    out_json.write_text(json.dumps(feed, indent=2) + "\n")
    print(f"prepared {out_apk.name} ({out_apk.stat().st_size / 1e6:.1f} MB) and update.json for version {name} ({code})")

    if a.scp:
        run(["scp", str(out_apk), a.scp])        # APK first, so nobody sees the feed before the file exists
        run(["scp", str(out_json), a.scp])
        print("uploaded. Feed:", a.base_url.rstrip("/") + "/update.json" if a.base_url else a.scp)
    elif a.github:
        if not shutil.which("gh"):
            sys.exit("GitHub CLI not found. Install it once:  brew install gh   then   gh auth login\n"
                     f"(files are already prepared in {DIST}; you can also upload them by hand)")
        repo = a.github
        run(["gh", "release", "create", f"v{name}", str(out_apk), "--repo", repo, "--title", f"MYLO VPN {name}", "--notes", a.notes or name])
        if subprocess.run(["gh", "release", "view", "update", "--repo", repo], capture_output=True).returncode != 0:
            run(["gh", "release", "create", "update", "--repo", repo, "--title", "update feed", "--notes", "do not delete"])
        run(["gh", "release", "upload", "update", str(out_json), "--repo", repo, "--clobber"])
        print(f"Feed URL for Defaults.UPDATE_URL: https://github.com/{repo}/releases/download/update/update.json")
    else:
        print(f"Upload BOTH files from {DIST} to {base}/ (apk first, update.json last).")


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    sub = ap.add_subparsers(dest="cmd", required=True)
    sub.add_parser("bump")
<<<<<<< HEAD
    au = sub.add_parser("auth-setup")
    au.add_argument("--url", required=True, help="https://api.your-domain.com")
    au.add_argument("--force", action="store_true")
    ov = sub.add_parser("ovpn-push")
    ov.add_argument("files", nargs="+")
    ov.add_argument("--scp", help="user@host:/opt/mylo/server/data/openvpn/")
=======
>>>>>>> origin/main
    st = sub.add_parser("setup")
    st.add_argument("--github", help="OWNER/REPO")
    st.add_argument("--base-url", help="https://YOUR-DOMAIN/mylo")
    p = sub.add_parser("publish")
    p.add_argument("--apk")
    p.add_argument("--notes")
    p.add_argument("--mandatory", action="store_true")
    p.add_argument("--base-url")
    p.add_argument("--scp", help="user@host:/remote/dir/")
    p.add_argument("--github", help="OWNER/REPO (needs the gh CLI logged in)")
    a = ap.parse_args()
<<<<<<< HEAD
    {"bump": lambda: bump(), "setup": lambda: setup(a), "auth-setup": lambda: auth_setup(a), "ovpn-push": lambda: ovpn_push(a), "publish": lambda: publish(a)}[a.cmd]()
=======
    {"bump": lambda: bump(), "setup": lambda: setup(a), "publish": lambda: publish(a)}[a.cmd]()
>>>>>>> origin/main


if __name__ == "__main__":
    main()

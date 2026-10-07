#!/usr/bin/env python3
"""
MYLO VPN - publish an update from your laptop.

  0) once:  python3 tools/release.py setup --github OWNER/REPO        (or  --base-url https://YOUR-DOMAIN/mylo)
            writes the update address into the app. Do it BEFORE the first release build.
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
    {"bump": lambda: bump(), "setup": lambda: setup(a), "publish": lambda: publish(a)}[a.cmd]()


if __name__ == "__main__":
    main()

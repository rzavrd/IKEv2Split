# MYLO VPN account server

Lets only the people you create accounts for use the app. Python 3, nothing to install.

## 1. Get a small server
Any Linux VPS (a few dollars a month) with a domain name pointing at it, for example `api.your-domain.com`.
It must be reachable from the phones **without** the VPN (the app signs in first).

## 2. Turn the login on in the app (on your laptop, once)
    python3 tools/release.py auth-setup --url https://api.your-domain.com
This copies the VPN account and the server list into `server/data/vpn.json` and removes them from the app,
so the APK no longer contains your VPN password. Then build and send the release APK once.

## 3. Run the server
Copy the `server/` folder to the VPS, then:
    python3 mylo_server.py serve --host 127.0.0.1 --port 8080 --trust-proxy
and put HTTPS in front (Android refuses plain http). The easiest is Caddy; `/etc/caddy/Caddyfile`:
    api.your-domain.com {
        reverse_proxy 127.0.0.1:8080
    }
Keep it running with systemd (`/etc/systemd/system/mylo.service`):
    [Unit]
    Description=MYLO account server
    After=network.target
    [Service]
    WorkingDirectory=/opt/mylo/server
    ExecStart=/usr/bin/python3 mylo_server.py serve --host 127.0.0.1 --port 8080 --trust-proxy
    Restart=always
    [Install]
    WantedBy=multi-user.target
then `systemctl enable --now mylo`.

## 4. Manage people (run on the VPS, in the server folder)
    python3 mylo_server.py add ali --days 30 --devices 2     # prints the password to give ali
    python3 mylo_server.py list
    python3 mylo_server.py extend ali --days 30
    python3 mylo_server.py disable ali                       # logs ali out immediately
    python3 mylo_server.py passwd ali
    python3 mylo_server.py reset-devices ali                 # if ali changed phone
    python3 mylo_server.py delete ali

## 5. OpenVPN files (from your laptop)
Each `.ovpn` must be a single file: the certificates and keys go *inside* it (`<ca>...</ca>`, `<cert>`, `<key>`, `<tls-auth>`), because the app only receives the text.
Name them like `DE-udp.ovpn` (the first two letters give the flag). On your laptop, in the project folder:

    python3 tools/release.py ovpn-push DE-udp.ovpn US-tcp.ovpn --scp user@your-vps:/opt/mylo/server/data/openvpn/

It checks the files first (and tells you what is missing) and uploads them. No app update is needed: signed-in users get them
within a few minutes (or when they reopen the app) under Locations > OpenVPN. On the VPS you can also use
`python3 mylo_server.py add-ovpn FILE.ovpn`, `list-ovpn`, `remove-ovpn NAME`.

On the phone, OpenVPN connections are made by the free "OpenVPN for Android" app: the user taps Import once per profile, then Connect.

## What this protects, honestly
- Without an account nobody gets the VPN password, the servers or the .ovpn files.
- Disabling an account logs it out within about 5 minutes (and stops a running VPN). The VPN account itself is shared:
  someone who was logged in could have copied it, so change the VPN password at your provider if you remove someone you do not trust.
- Keep `server/data/` private and back it up (`mylo.db` holds the users).

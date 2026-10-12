# MYLO VPN

**MYLO VPN** یک اپلیکیشن مدرن VPN برای Android است که با تمرکز بر اتصال سریع، مدیریت ساده پروفایل‌ها، نمایش وضعیت اتصال و تجربه کاربری حرفه‌ای طراحی شده است.

## ✨ قابلیت‌ها

* 🔐 اتصال VPN و مدیریت وضعیت Tunnel
<<<<<<< HEAD
* 🔑 پروتکل‌ها: **IKEv2**، **OpenVPN**، **VMess** و **VLESS** (انتخاب از صفحه اصلی و Profile)
=======
>>>>>>> origin/main
* 🌍 انتخاب و مدیریت Location / Server
* ⚡ نمایش وضعیت اتصال و اطلاعات شبکه
* 📊 نمایش آمار و اطلاعات ترافیک
* 📡 تست Ping و بررسی وضعیت سرور
* 🎨 رابط کاربری مدرن و قابل شخصی‌سازی
* 🌙 پشتیبانی از حالت‌های مختلف ظاهر
* 🔄 بررسی خودکار نسخه‌های جدید
* 📥 دانلود نسخه جدید از داخل برنامه
* 🛠️ صفحه تنظیمات و مدیریت پروفایل
<<<<<<< HEAD
* 📝 نمایش Log و اطلاعات اتصال (و گزارش کرش)
* 🗺️ نمایش موقعیت‌ها و مناطق مختلف
* 👤 ورود با نام کاربری و رمز (اختیاری، با سرور حساب)

## 🔑 پروتکل‌ها

| پروتکل | چگونه کار می‌کند |
| ------ | ---------------- |
| IKEv2 | داخل خود MYLO VPN اجرا می‌شود (Android 11+) |
| OpenVPN | پروفایل‌ها از سرور یا فایل `.ovpn`؛ اتصال با برنامه رایگان OpenVPN for Android |
| VMess / VLESS | لینک `vmess://` و `vless://` را دستی اضافه کنید؛ لینک کپی می‌شود و در v2rayNG / Hiddify / NekoBox باز می‌شود |
=======
* 📝 نمایش Log و اطلاعات اتصال
* 🗺️ نمایش موقعیت‌ها و مناطق مختلف
>>>>>>> origin/main

## 🔄 سیستم آپدیت

MYLO VPN دارای سیستم به‌روزرسانی داخلی است.

برنامه به‌صورت دوره‌ای نسخه جدید را بررسی می‌کند و همچنین کاربر می‌تواند از داخل برنامه به صورت دستی آپدیت را بررسی کند.

Update Feed:

`https://github.com/rzavrd/IKEv2Split/releases/download/update/update.json`

نسخه‌های منتشرشده در بخش Releases قرار می‌گیرند.

## 📦 آخرین نسخه

<<<<<<< HEAD
**MYLO VPN 1.5**

آخرین نسخه را می‌توانید از بخش Releases دریافت کنید. توضیح تغییرات هر نسخه داخل برنامه هم هست: Profile > About & What's new.
=======
**MYLO VPN 1.4**

آخرین نسخه را می‌توانید از بخش Releases دریافت کنید.
>>>>>>> origin/main

## 🏗️ ساخت پروژه

پروژه با Android Studio و Kotlin توسعه داده شده است.

برای ساخت نسخه Release:

1. پروژه را در Android Studio باز کنید.
2. Build Variant را روی `release` قرار دهید.
3. APK را Build کنید.

فایل Release در مسیر مشابه زیر قرار می‌گیرد:

```text
app/build/outputs/apk/release/app-release.apk
```

## 🚀 انتشار نسخه جدید

سیستم انتشار پروژه از طریق GitHub Release مدیریت می‌شود.

پس از افزایش نسخه:

```bash
python3 tools/release.py bump
```

<<<<<<< HEAD
نسخه Release را در Android Studio بسازید، سپس اجرا کنید:
=======
نسخه Release را در Android Studio بسازید، سپس APK را در مسیر مورد انتظار قرار دهید و اجرا کنید:
>>>>>>> origin/main

```bash
python3 tools/release.py publish --github rzavrd/IKEv2Split --notes "توضیحات تغییرات"
```

این اسکریپت Release مربوط به نسخه جدید و Update Feed را مدیریت می‌کند.
<<<<<<< HEAD
برای هر نسخه، تغییرات را در `app/src/main/java/com/ikev2split/app/ui/AboutScreen.kt` (لیست `releases`) هم اضافه کنید تا داخل برنامه نمایش داده شود.

## 👤 حساب کاربری و فایل‌های OpenVPN

* ورود با نام کاربری/رمز تا وقتی `python3 tools/release.py auth-setup --url https://api.your-domain.com` را اجرا نکنید **خاموش** است. راهنما: `server/README.md`.
* آپلود فایل‌های OpenVPN از لپ‌تاپ: `python3 tools/release.py ovpn-push FILE.ovpn --scp user@host:/opt/mylo/server/data/openvpn/`

## ⚠️ امنیت

* فایل‌های `keystore.properties`، `*.jks` و `server/data/` در `.gitignore` هستند. هرگز آن‌ها را در گیت‌هاب نگذارید.
* اگر رمز VPN داخل `Defaults.kt` باشد، هر کسی APK یا مخزن عمومی را ببیند آن را می‌بیند. `auth-setup` آن را از برنامه برمی‌دارد.
=======
>>>>>>> origin/main

## 📋 نسخه‌ها

| Version | Status   |
| ------- | -------- |
<<<<<<< HEAD
| 1.5     | Current  |
| 1.4     | Previous |
=======
| 1.4     | Current  |
| 1.3     | Previous |
>>>>>>> origin/main

## 🔒 وضعیت پروژه

این پروژه در حال توسعه فعال است و برخی قابلیت‌ها ممکن است در نسخه‌های آینده تغییر یا بهبود پیدا کنند.

---

**MYLO VPN**
Modern. Simple. Secure.
<<<<<<< HEAD
=======

>>>>>>> origin/main

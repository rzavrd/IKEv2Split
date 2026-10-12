package com.ikev2split.app.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ikev2split.app.update.Updater

const val GITHUB_URL = "https://github.com/rzavrd/IKEv2Split"
const val UPDATE_FEED = "https://github.com/rzavrd/IKEv2Split/releases/download/update/update.json"

private class Release(val version: String, val items: List<Pair<String, String>>)

/** Newest first. Each item: Persian text to English text. Add a Release here for every version you publish. */
private val releases = listOf(
    Release(
        "1.5",
        listOf(
            "رفع بسته‌شدن برنامه هنگام روشن‌کردن VPN در گوشی‌های قدیمی؛ اگر خطایی رخ دهد پیام آن نمایش داده می‌شود و گزارش در Connection Log می‌آید." to
                "Fixed the app closing when the VPN is switched on in older phones. Errors are now shown, and a crash report appears in Connection Log.",
            "انتخاب پروتکل از صفحه اصلی و پروفایل: IKEv2، OpenVPN و VMess / VLESS." to
                "Choose the protocol from the home screen and Profile: IKEv2, OpenVPN and VMess / VLESS.",
            "OpenVPN: پروفایل‌ها از سرور یا از فایل .ovpn؛ اتصال توسط برنامه OpenVPN for Android انجام می‌شود." to
                "OpenVPN: profiles from the server or an .ovpn file; the connection is made by OpenVPN for Android.",
            "افزودن دستی لینک vmess:// و vless:// (Locations > V2Ray)؛ لینک کپی می‌شود و در v2rayNG / Hiddify / NekoBox باز می‌شود." to
                "Add vmess:// and vless:// links by hand (Locations > V2Ray). The link is copied and opened in v2rayNG / Hiddify / NekoBox.",
            "ورود با نام کاربری و رمز (اختیاری): فقط کاربرانی که مدیر ساخته می‌توانند از برنامه استفاده کنند." to
                "Optional sign-in with username and password: only people the administrator created can use the app.",
            "بارگذاری فایل‌های OpenVPN از لپ‌تاپ برای همه کاربران با یک دستور." to
                "Upload OpenVPN files from a laptop for all users with one command.",
            "صفحه درباره برنامه و تازه‌ها (همین صفحه)." to
                "This About & What's new page.",
        ),
    ),
)

@Composable
fun AboutScreen() {
    val ctx = LocalContext.current
    var dev by remember { mutableStateOf(false) }
    fun open(url: String) = ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    val installed = try { Updater.installedName(ctx) } catch (e: Exception) { "?" }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("MYLO VPN", fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Text("Version $installed", color = Muted)
        Text(
            "MYLO VPN یک اپلیکیشن مدرن VPN برای Android است که با تمرکز بر اتصال سریع، مدیریت ساده پروفایل‌ها، نمایش وضعیت اتصال و تجربه کاربری حرفه‌ای طراحی شده است.",
            fontSize = 15.sp,
        )

        Card("✨ قابلیت‌ها") {
            listOf(
                "🔐 اتصال VPN و مدیریت وضعیت Tunnel",
                "🌍 انتخاب و مدیریت Location / Server",
                "⚡ نمایش وضعیت اتصال و اطلاعات شبکه",
                "📊 نمایش آمار و اطلاعات ترافیک",
                "📡 تست Ping و بررسی وضعیت سرور",
                "🎨 رابط کاربری مدرن و قابل شخصی‌سازی",
                "🌙 پشتیبانی از حالت‌های مختلف ظاهر",
                "🔄 بررسی خودکار نسخه‌های جدید",
                "📥 دانلود نسخه جدید از داخل برنامه",
                "🛠️ صفحه تنظیمات و مدیریت پروفایل",
                "📝 نمایش Log و اطلاعات اتصال",
                "🗺️ نمایش موقعیت‌ها و مناطق مختلف",
                "🔑 پروتکل‌ها: IKEv2، OpenVPN، VMess و VLESS",
            ).forEach { Text(it, fontSize = 14.sp) }
        }

        Card("🔄 سیستم آپدیت") {
            Text("MYLO VPN دارای سیستم به‌روزرسانی داخلی است. برنامه به‌صورت دوره‌ای نسخه جدید را بررسی می‌کند و همچنین کاربر می‌تواند از داخل برنامه (Profile > Check for updates) به صورت دستی آپدیت را بررسی کند.", fontSize = 14.sp)
            Text("Update Feed:", color = Muted, fontSize = 12.sp)
            Text(UPDATE_FEED, fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = Muted)
            Text("نسخه‌های منتشرشده در بخش Releases قرار می‌گیرند.", fontSize = 14.sp)
        }

        Card("🆕 تازه‌ها / What's new") {
            releases.forEach { r ->
                Text("Version ${r.version}", fontWeight = FontWeight.Bold)
                r.items.forEach { (fa, en) ->
                    Column(Modifier.padding(bottom = 6.dp)) {
                        Text("• $fa", fontSize = 14.sp)
                        Text(en, fontSize = 12.sp, color = Muted)
                    }
                }
            }
            Text("نسخه‌های قبلی: بخش Releases در GitHub  /  Older versions: GitHub Releases", color = Muted, fontSize = 12.sp)
        }

        Card("📋 نسخه‌ها") {
            Text("1.5 — Current", fontSize = 14.sp)
            Text("1.4 — Previous", fontSize = 14.sp, color = Muted)
            Text("نسخه نصب‌شده روی این گوشی: $installed", fontSize = 12.sp, color = Muted)
        }

        Column(Modifier.fillMaxWidth().clip(CardShape).background(CardBg).clickable { dev = !dev }.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("🏗️ برای توسعه‌دهندگان", Modifier.weight(1f), fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                Icon(if (dev) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown, null)
            }
            if (dev) {
                Text(
                    "پروژه با Android Studio و Kotlin توسعه داده شده است. برای ساخت نسخه Release: پروژه را باز کنید، Build Variant را روی release بگذارید و APK را Build کنید.\n\n" +
                        "app/build/outputs/apk/release/app-release.apk\n\n" +
                        "انتشار نسخه جدید (GitHub Release):\n" +
                        "python3 tools/release.py bump\n" +
                        "python3 tools/release.py publish --github rzavrd/IKEv2Split --notes \"توضیحات تغییرات\"",
                    Modifier.padding(top = 10.dp), fontSize = 12.sp, color = Muted,
                )
            }
        }

        Text("🔒 این پروژه در حال توسعه فعال است و برخی قابلیت‌ها ممکن است در نسخه‌های آینده تغییر یا بهبود پیدا کنند.", fontSize = 13.sp, color = Muted)

        OutlinedButton({ open(GITHUB_URL) }, Modifier.fillMaxWidth()) { Text("GitHub — rzavrd/IKEv2Split") }
        OutlinedButton({ open("$GITHUB_URL/releases") }, Modifier.fillMaxWidth()) { Text("Releases") }

        Text("MYLO VPN\nModern. Simple. Secure.", Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 16.dp), color = Muted, fontSize = 12.sp)
    }
}

@Composable
private fun Card(title: String, content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(CardShape).background(CardBg).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(title, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
        content()
    }
}

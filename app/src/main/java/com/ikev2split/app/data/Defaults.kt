package com.ikev2split.app.data

import java.util.Locale

/** Built-in account + server list (PureVPN IKEv2). Anyone with this APK can extract these. */
object Defaults {
    const val USER = "purevpn0s13929385"
    const val PASS = "R4\$TDw{IwUI0st"
    const val RID = "pointtoserver.com"
    /** Where update.json lives (must be https). Replace YOUR-DOMAIN before the first release build. */
    const val UPDATE_URL = "https://github.com/mehdi/IKEv2Split/releases/download/update/update.json"

    // cc|host-prefix   (full host = prefix + ".ptoserver.com"), ordered by latency from the router
    private const val LIST = """
TR|sx160591-ikev
TR|tr2-auto-ikev
SE|se2-auto-ikev
CH|ch2-auto-ikev
NL|nl2-auto-ikev
DE|eg2-auto-ikev
DE|om2-auto-ikev
RO|sx070667-ikev
NO|no2-auto-ikev
GB|ukl2-auto-ikev
AT|at2-auto-ikev
DE|mc2-auto-ikev
GB|ukm2-auto-ikev
DE|pr2-auto-ikev
SE|sx060289-ikev
IE|ie2-auto-ikev
BE|be-ikev-pf
GB|uk2-auto-ikev
FR|fr2-auto-ikev
LU|lu-ikev
LT|lt-ikev
IT|it-ikev-pf
SK|sk2-auto-ikev
HU|hu2-auto-ikev
LV|lv2-auto-ikev
EE|ee2-auto-ikev
BG|bg-ikev
DK|dk-ikev
PT|pt2-auto-ikev
DE|de2-auto-ikev
US|us2-auto-ikev
CA|cato2-auto-ikev
NG|ng2-auto-ikev
US|sx017451-ikev
CA|cav2-auto-ikev
RS|rs-ikev
US|vg-ikev
US|usfl2-auto-ikev
AE|ae2-auto-ikev
US|bo2-auto-ikev
NL|in2-auto-ikev
US|sx0112801-ikev
BN|bn-ikev
US|ussf2-auto-ikev
US|ky2-auto-ikev
CA|ca2-auto-ikev
ZA|za-ikev
US|bm2-auto-ikev
SG|sg-ikev-pf
US|pa2-auto-ikev
SG|ph2-auto-ikev
US|usphx2-auto-ikev
US|ustx2-auto-ikev
US|aw2-auto-ikev
CL|cl2-auto-ikev
HK|hk2-auto-ikev
BR|br-ikev
AR|ar2-auto-ikev
JP|jp-ikev-pf
KR|kr2-auto-ikev
AU|aubn2-auto-ikev
US|uswdc2-auto-ikev
AU|au2-auto-ikev
AU|aume2-auto-ikev
AU|ausd2-auto-ikev
US|usga2-auto-ikev
US|usnj2-auto-ikev
US|usva2-auto-ikev
US|usil2-auto-ikev
US|usny2-auto-ikev
US|ussa2-auto-ikev
US|usut2-auto-ikev
AU|aupe2-auto-ikev
US|usca2-auto-ikev
GR|gr2-auto-ikev
US|ao2-auto-ikev
US|bb2-auto-ikev
"""

    // second list: cc|host  (empty cc = unknown country). Hosts with a dot are used as given.
    private const val LIST2 = """
US|sx013365-ikev
US|sx017451-ikev
US|sx019630-ikev
US|sx017501-ikev
US|sx0140122-ikev
US|sx019543-ikev
|sx023501-ikev
|sx0224209-ikev
AU|sx152501-ikev
AU|sx152610-ikev
AU|sx152416-ikev
AU|sx152201-ikev
DE|sx0430139-ikev
CA|sx032111-ikev
CA|sx0320145-ikev
CA|sx032322-ikev
NL|sx0517119-ikev
HK|sx241130-ikev
MY|sx110990-ikev
BE|sx400501-ikev
FR|sx1730195-ikev
SE|sx060289-ikev
JP|sx331416-ikev
SG|sx121001-ikev
IT|sx250531-ikev
IT|sx250797-ikev
IE|sx1302104-ikev.dnsdialer.com
LU|sx140301-ikev
IN|sx591801-ikev
AT|sx490305-ikev
DK|sx410701-ikev
NO|sx420632-ikev
RO|sx070682-ikev
RU|sx100837-ikev
RU|sx101031-ikev
PL|sx380101-ikev
CZ|sx390434-ikev
FI|sx470502-ikev
ES|sx460484-ikev
CH|sx080619-ikev
UA|sx230201-ikev
NZ|sx340546-ikev
MX|sx360633-ikev
"""

    /** All servers, de-duplicated by address, named "Country" or "Country - n". */
    fun servers(): List<Server> {
        val seen = LinkedHashMap<String, String>()   // address -> cc
        (LIST + "\n" + LIST2).lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.forEach { l ->
            val f = l.split("|")
            val cc = f[0]
            val h = f[1]
            val host = if (h.contains('.')) h else "${h}.ptoserver.com"
            if (host !in seen) seen[host] = cc
        }
        val total = seen.values.groupingBy { it }.eachCount()
        val idx = HashMap<String, Int>()
        return seen.map { (host, cc) ->
            val k = (idx[cc] ?: 0) + 1
            idx[cc] = k
            val base = if (cc.isEmpty()) "Other" else countryName(cc)
            Server(if ((total[cc] ?: 1) > 1) "$base - $k" else base, host, cc)
        }
    }
}

/** Country code from a hostname prefix like "uk2-auto-ikev..." -> GB. Empty if unknown. */
fun guessCc(host: String): String {
    if (host.startsWith("sx")) return ""
    var c = host.take(2).uppercase()
    if (c == "UK") c = "GB"
    return if (c in Locale.getISOCountries()) c else ""
}

import com.vueo.shared.core.player.AddonPlaybackPolicy
import com.vueo.shared.core.extensions.AddonAddressText
fun main() {
 var n=0
 fun test(name:String, f:()->Unit) { f();n++;println("PASS $name") }
 test("Addon uses network startup") {check(AddonPlaybackPolicy.usesNetworkStartup("org.stremio.example"))}
 test("Plugin retains existing first-frame deadline") {check(!AddonPlaybackPolicy.usesNetworkStartup("plugin:123:vidlove"))}
 test("Unknown source retains existing deadline") {check(!AddonPlaybackPolicy.usesNetworkStartup(""))}
 test("Network timeout is fifteen seconds") {check(AddonPlaybackPolicy.NETWORK_TIMEOUT_MS==15000)}
 test("LAN address separated without modifying query") {val p=AddonAddressText.parse("Stream on LAN. Address: http://192.168.0.2:7000/a?k=A%2FB+Z");check(p.description=="Stream on LAN.");check(p.address=="http://192.168.0.2:7000/a?k=A%2FB+Z")}
 test("HTTPS server address separated") {check(AddonAddressText.parse("Stream on server. Address: https://server.test/").address=="https://server.test/")}
 test("Address on existing separate line") {check(AddonAddressText.parse("Description\nADDRESS: https://server.test/").description=="Description")}
 test("No address leaves description unchanged") {check(AddonAddressText.parse("Plain description")==AddonAddressText("Plain description",null))}
 test("Missing description creates no row") {check(AddonAddressText.parse(null)==AddonAddressText("",null))}
 test("Embedded prose URL is not extracted as address") {check(AddonAddressText.parse("Visit https://example.test/ for details").address==null)}
 println("$n checks passed")
}

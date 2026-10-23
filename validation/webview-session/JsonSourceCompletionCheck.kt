package com.vueo.shared.core.plugin
import org.json.JSONObject

fun main() {
    val rule = JSONObject("""{"requestMatcher":"https://api.example/movie?id=12345&mode=json","identityPath":"meta.id","identityValue":"12345","sourcePath":"source","originalLanguagePath":"meta.original_language","minimumHeight":720}""")
    val request = "https://api.example/movie?id=12345&mode=json&sources=moviebox&vh=1"
    val hosts = listOf("media.example")
    fun payload() = JSONObject("""{"meta":{"id":12345,"original_language":"en"},"source":{"url":"https://media.example/api?token=auto","qualities":[{"url":"https://media.example/api?token=1080","type":"mp4","quality":"1080p"},{"url":"https://media.example/api?token=480","type":"mp4","quality":"480p"}]}}""")
    fun eligible(body: JSONObject, url: String = request, rules: JSONObject? = rule, allowed: List<String> = hosts) = JsonSourceCompletionPolicy.eligible(url, body, rules, allowed)
    var count = 0
    fun verify(name: String, expected: Boolean, actual: Boolean) {check(expected == actual) {name};count++;println("PASS $name")}
    verify("typed extensionless 1080p source can complete discovery",true,eligible(payload()))
    verify("opt in required",false,eligible(payload(),rules=null))
    verify("empty source cannot finish",false,eligible(JSONObject("""{"meta":null,"source":null}""")))
    verify("different request ID",false,eligible(payload(),request.replace("id=12345","id=999")))
    verify("duplicate request identity",false,eligible(payload(),request+"&id=999"))
    verify("different API host",false,eligible(payload(),request.replace("api.example","other.example")))
    verify("different API path",false,eligible(payload(),request.replace("/movie?","/movie2?")))
    verify("plain HTTP rejected",false,eligible(payload(),request.replace("https:","http:")))
    verify("metadata mismatch",false,eligible(payload().apply {getJSONObject("meta").put("id",999)}))
    verify("metadata identity required",false,eligible(payload().apply {getJSONObject("meta").remove("id")}))
    verify("host allowlist required",false,eligible(payload(),allowed=emptyList()))
    verify("media host mismatch",false,eligible(payload(),allowed=listOf("other.example")))
    verify("known low renditions only",false,eligible(payload().apply {getJSONObject("source").getJSONArray("qualities").remove(0)}))
    verify("unknown quality with explicit type passes",true,eligible(payload().apply {getJSONObject("source").getJSONArray("qualities").getJSONObject(0).remove("quality")}))
    verify("direct 480p rejected",false,eligible(payload().apply {getJSONObject("source").apply {remove("qualities");put("type","mp4");put("quality","480p")}}))
    verify("adaptive master preserved as auto",true,eligible(payload().apply {getJSONObject("source").apply {remove("qualities");put("manifest","#EXTM3U\n#EXT-X-STREAM-INF:BANDWIDTH=1000\nchild")}}))
    verify("untyped extensionless URL rejected",false,eligible(payload().apply {getJSONObject("source").remove("qualities")}))
    verify("dub only rejected",false,eligible(payload().apply {getJSONObject("source").put("audioLanguage","hi")}))
    verify("dub flag rejected",false,eligible(payload().apply {getJSONObject("source").put("isDubbed",true)}))
    verify("original audio retained",true,eligible(payload().apply {getJSONObject("source").put("audioLanguage","eng")}))
    verify("multi audio including original retained",true,eligible(payload().apply {getJSONObject("source").put("audioLanguages",org.json.JSONArray("[\"hi\",\"en\"]"))}))
    verify("multi audio without original rejected",false,eligible(payload().apply {getJSONObject("source").put("audioLanguages",org.json.JSONArray("[\"hi\",\"ta\"]"))}))
    verify("variant audio overrides parent",false,eligible(payload().apply {getJSONObject("source").getJSONArray("qualities").getJSONObject(0).put("audioLanguage","hi")}))
    verify("credential URL rejected",false,eligible(payload().apply {getJSONObject("source").getJSONArray("qualities").getJSONObject(0).put("url","https://user:pass@media.example/api")}))
    verify("do not finish for lower original rendition when chosen higher one is dubbed",false,eligible(payload().apply {
        getJSONObject("source").getJSONArray("qualities").apply {
            getJSONObject(0).put("audioLanguage","hi")
            getJSONObject(1).put("quality","720p").put("audioLanguage","en")
        }
    }))
    verify("H264 preference matches provider before audio check",true,eligible(payload().apply {
        getJSONObject("source").getJSONArray("qualities").put(JSONObject("""{"url":"https://media.example/hevc.mp4","type":"mp4","quality":"2160p","codec":"hevc","audioLanguage":"hi"}"""))
    }))
    val tv = JSONObject(rule.toString()).put("requestMatcher","https://api.example/tv?id=12345&season=0&episode=2&mode=json")
    verify("season zero exact episode",true,eligible(payload(),"https://api.example/tv?id=12345&season=0&episode=2&mode=json&sources=moviebox",tv))
    verify("wrong TV episode rejected",false,eligible(payload(),"https://api.example/tv?id=12345&season=0&episode=3&mode=json&sources=moviebox",tv))
    println("$count JSON source completion checks passed")
}

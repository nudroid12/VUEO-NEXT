from pathlib import Path
import sys
root = Path(__file__).resolve().parents[2]
plugin = root / "shared/core/src/main/java/com/vueo/shared/core/plugin"
source = (plugin / "PluginRuntime.kt").read_text()
start = source.index('        return """', source.index('    private fun buildRuntimeScript(')) + len('        return """')
script = source[start:].split('        """.trimIndent()', 1)[0]
timers = (plugin / "ProviderExecutionTasks.kt").read_text().split('internal val PROVIDER_TIMER_SCRIPT = """', 1)[1].split('""".trimIndent()', 1)[0]
for key, value in {"PROVIDER_TIMER_SCRIPT": timers, "safeTmdbId": '\"123\"', "safeMediaType": '\"movie\"', "seasonValue": "1", "episodeValue": "1"}.items():
    script = script.replace("${" + key + "}", value)
Path(sys.argv[1]).write_text(script)

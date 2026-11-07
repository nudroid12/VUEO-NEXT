package com.vueo.shared.core.diagnostics
object PerformanceDiagnostics { var enabled=true; fun isCollecting()=enabled }
object ProviderDiagnostics { fun isCollecting()=false }
object RuntimeDiagnostics {
 val events = java.util.concurrent.CopyOnWriteArrayList<String>()
 fun recordPlayerEvent(platform: String,event: String,details: String) { events.add("$platform $event $details") }
}

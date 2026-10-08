package com.vueo.shared.core.diagnostics

import android.content.Context

/** UI adapter; each recorder retains its own preferences, buffer and export. */
class DiagnosticsViewer(val providerMode: Boolean) {
    data class Tab(val name: String, val label: String)
    val title: String = if (providerMode) "Provider Diagnose" else "Performance Diagnostics"
    val kind: String = if (providerMode) "provider" else "performance"
    val tabs: List<Tab> = if (providerMode) ProviderDiagnostics.Tab.entries.map { Tab(it.name, it.label) }
        else (listOf(PerformanceDiagnostics.Tab.FULL) + PerformanceDiagnostics.Tab.entries.filter { it != PerformanceDiagnostics.Tab.FULL })
            .map { Tab(it.name, it.label) }
    private fun provider(tab: Tab) = ProviderDiagnostics.Tab.valueOf(tab.name)
    private fun performance(tab: Tab) = PerformanceDiagnostics.Tab.valueOf(tab.name)
    fun isEnabled(context: Context) = if (providerMode) ProviderDiagnostics.isEnabled(context) else PerformanceDiagnostics.isEnabled(context)
    fun isCollecting() = if (providerMode) ProviderDiagnostics.isCollecting() else PerformanceDiagnostics.isCollecting()
    fun setEnabled(context: Context, value: Boolean) {
        if (providerMode) ProviderDiagnostics.setEnabled(context, value) else PerformanceDiagnostics.setEnabled(context, value)
    }
    fun previewRaw(tab: Tab, maxEvents: Int = 220) = if (providerMode) ProviderDiagnostics.previewRaw(provider(tab), maxEvents) else PerformanceDiagnostics.previewRaw(performance(tab), maxEvents)
    fun previewSummary(tab: Tab) = if (providerMode) ProviderDiagnostics.previewSummary(provider(tab)) else PerformanceDiagnostics.previewSummary(performance(tab))
    fun exportRaw(tab: Tab) = if (providerMode) ProviderDiagnostics.exportRaw(provider(tab)) else PerformanceDiagnostics.exportRaw(performance(tab))
    fun exportSummary(tab: Tab) = if (providerMode) ProviderDiagnostics.exportSummary(provider(tab)) else PerformanceDiagnostics.exportSummary(performance(tab))
    fun clear() { if (providerMode) ProviderDiagnostics.clear() else PerformanceDiagnostics.clear() }
    fun saveBundle(context: Context): PerformanceDiagnostics.SaveResult {
        return if (providerMode) ProviderDiagnostics.saveBundle(context).let { PerformanceDiagnostics.SaveResult(it.displayName, it.location) }
            else PerformanceDiagnostics.saveBundle(context)
    }
}

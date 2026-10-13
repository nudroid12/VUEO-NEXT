package com.vueo.shared.core.sync

/** Public project identity only. User credentials are never bundled. */
internal object VueoSyncConfig {
    const val URL = "https://rxsxjkhcuobwbdoinxez.supabase.co"
    const val KEY = "sb_publishable_krGd6Ys2_Sb8fwnT47IMug_mJBFnIx5"
    const val EMAIL_REDIRECT = "vueo://account/confirmed"
    val categories = linkedMapOf(
        "profiles" to "Profiles", "favorites" to "Favorites",
        "progress" to "Continue Watching", "history" to "Watched History",
        "addons" to "Installed Addons & Providers",
        "integrations" to "API Keys", "trakt" to "Trakt Account",
    )
}

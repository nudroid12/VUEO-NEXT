package com.vueo.shared.core.sync

/** Public project identity only. User credentials are never bundled. */
internal object VueoSyncConfig {
    const val URL = "https://rxsxjkhcuobwbdoinxez.supabase.co"
    const val KEY = "sb_publishable_krGd6Ys2_Sb8fwnT47IMug_mJBFnIx5"
    val categories = linkedMapOf(
        "profiles" to "Profiles", "favorites" to "Favorites",
        "progress" to "Continue Watching", "history" to "Watched History",
        "addons" to "Addons & Providers", "catalogs" to "Catalogs",
        "integrations" to "TMDB & MDBList", "trakt" to "Trakt Connection & Options",
    )
}

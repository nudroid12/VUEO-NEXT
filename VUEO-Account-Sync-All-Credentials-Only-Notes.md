# Account sync: one control, credentials only

TV and mobile Account now show account identity, sync state, a concise shared-data summary,
Sync now, Turn sync on/off and sign-out confirmation. Per-category switches are removed.
The single control enables/disables all supported account data together.

Shared: profiles, Favorites, Continue Watching, watched history, installed addons/providers,
TMDB and MDBList API keys, Gemini API key, and Trakt account connection.
Local: all enhancement toggles, Trakt discovery/history/scrobble options, addon/provider ON/OFF,
catalog preferences, subtitle/audio/player settings and device preferences.

Gemini: only the API key is transferred through the existing encrypted account sync pipeline.
Worker token, expiry, model/session and SmartSubs enabled state are not transferred.
An imported key invalidates this device's previous worker session without changing its saved ON/OFF
preference. Test connection in Gemini on the receiving device before using SmartSubs.
Gemini screens refresh after received integration changes while preserving edits in progress.

Existing accounts: if any old sync category was enabled, all supported categories are now enabled.
If all were disabled, sync remains off. New account sign-ins enable all supported categories.
Turning sync off pauses transfers on this device and keeps its data. Turning it on captures local
changes and resumes sync. Trakt account changes while paused are queued locally for resume.
This uses the existing debounced foreground/delta sync schedule; no new live subscriptions.

Legacy enhancement-toggle records are removed once from the local sync journal and ignored on import.
No cloud deletion or local toggle reset is performed by that migration.
Remote Trakt disconnect preserves this device's feature options.

Apply to both mobile and TV builds. Repository-root paths; no enclosing repo folder, YAML,
SQL, worker changes or Supabase migration. Existing Account summary polish, sidebar,
Content Manager rollback, Favorites heart and DNA detail patches are not overwritten.

Validation: 36 source allowlist cases passed (keys/account metadata accepted; feature toggles rejected).
Static checks verified all-or-none control, encrypted API-key path, local toggle preservation,
legacy-journal restriction and balanced Kotlin delimiters; ZIP integrity checked.
Android compilation, real Supabase exchanges and physical TV/mobile layout testing were unavailable.

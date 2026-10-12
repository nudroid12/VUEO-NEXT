# VUEO Sync — TV and mobile

Run `migrations/20261012_vueo_sync_v2.sql` in the same Supabase project as the earlier setup. This is additive; existing v1 tables and local app data are retained. Expected SQL result: `VUEO sync v2 setup complete`.

Settings → Account: create/sign in to a VUEO account, then enable the categories wanted on each device. Signup may require email confirmation, depending on Supabase Auth configuration. Sync switches start OFF for a new account on a device. Enabling any category also enables Profiles; disabling Profiles disables all categories.

Included: profiles (name, avatar, kids flag, existing order), Favorites, Continue Watching, history and watched marks, addon manifest URLs, provider repository descriptors, TMDB/MDBList keys and integration settings, and Trakt sessions/options. Profile PINs and active-profile selection remain device-local. Addon/repository/provider enable flags, catalog choices and ordering, subtitle, audio, player preferences, appearance, decoder, cache, downloads and diagnostics are excluded. Existing legacy cloud switches/catalog records are ignored, and legacy outbox entries for those settings are discarded. Provider code is downloaded normally on the destination device; it is not uploaded to Supabase.

Local changes persist first. Foreground polling reads only changes since the last cloud cursor. App sync is deferred during playback and briefly on exit to let player saves finish. Changes arriving during a request enter a later batch. Conflicting stale edits accept the cloud revision; a subsequent explicit edit can change it. Tombstones prevent stale devices restoring removed records. Enable/disable choices apply to this device.

User sessions and the outbox are encrypted with Android Keystore and stored outside Android backup. Cloud record bodies are encrypted with per-account database keys in a private schema, accessed only by authenticated owner-checking RPCs. This is server-side encryption, not end-to-end encryption: the database administrator retains access. No service-role key is bundled.

Trakt access tokens stay local for normal requests. Cloud leases coordinate token refresh and connection changes across devices while Trakt connection sync is enabled. If shared refresh is unavailable, it does not fall back to independently rotating a shared refresh token. Existing Trakt watchlist/history/playback/scrobble controls remain independent from VUEO Sync.

Validation: static source/delimiter checks, SQL access-control review, patch path/integrity checks and a seven-case protocol model (additions, tombstones, re-add, rewind, retry, account isolation and delta cursors). No Android compilation, live SQL execution or device test was performed. Verify signup/login, cross-device changes, removals, offline reconnect and Trakt refresh on devices before treating this as production-validated.

## Email confirmation redirect

In Supabase → Authentication → URL Configuration → Redirect URLs, add exactly `vueo://account/confirmed` and save. Keep email confirmation enabled. The updated signup request sends this explicit redirect URL. Confirming a new email link on a phone with the updated mobile app installed opens Account; sign in normally there. Callback tokens are not imported or logged. Existing confirmation emails keep their old redirect, so this applies to new emails. TV signups can also be confirmed on a phone with the mobile app; return to the TV and sign in normally. No new SQL migration is required for these app changes.

# VUEO-NEXT Architecture

VUEO-NEXT is the clean monorepo for the VUEO ecosystem.

## Applications

- `mobile/` -> Android Mobile application, `com.vueo.app`
- `tv/` -> Android TV application, `com.vueo.tv`
- `shared/core/` -> reusable VUEO domain and runtime logic, no application ID

## Dependency rule

Both applications may depend on `shared/core`.

`shared/core` must never depend on `mobile` or `tv`.

```text
              shared/core
               /       \
          mobile       tv
```

UI, navigation, input handling and platform-specific presentation remain inside the relevant application module.

Reusable domain logic, source discovery, Stremio integration, provider runtime, ranking, profiles, library state and playback state migrate into shared modules in controlled slices.

## Migration safety rule

The existing `VUEO` repository is the known-good reference during migration. It is not rewritten as part of VUEO-NEXT migration.

Mobile parity is mandatory. Mature Mobile behaviour must be preserved before a migrated subsystem is considered complete.

Every shared-core migration must keep both application modules buildable.

## Locked package IDs

| Product | Application ID |
| --- | --- |
| VUEO Mobile | `com.vueo.app` |
| VUEO TV | `com.vueo.tv` |

These IDs must not be casually changed after release/signing is established.
## Mobile UI decomposition checkpoint

Mobile remains a single Compose application surface, but large screen families may be extracted from `VueoApp.kt` when the extraction preserves existing contracts.

Current extracted family:

- `ui/VueoContentManager.kt` contains Content Manager, Addons, Plugins/Providers, Catalog Order and Provider Diagnostics presentation.

This is a file-level decomposition only. Stores, Shared Core runtime and navigation behaviour remain unchanged. Avoid broad rewrites during decomposition.


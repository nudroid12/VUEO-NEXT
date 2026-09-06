# TV 29C.3a Build Fix

Scope: compile-only fix for 29C.3 Collapsible Sidebar + 29C.2a Update Prompt.

Changes:
- `TvTopBar.kt`: remove the explicit `androidx.compose.foundation.layout.weight` import. `Modifier.weight(...)` is used inside `ColumnScope`, where the scoped extension is already available; the explicit import resolved to an internal parent-data symbol on the current Compose toolchain.
- `TvUpdatePrompt.kt`: import `androidx.compose.ui.input.key.type` so `event.type` resolves for D-pad/Back activation handling.

No navigation behavior, animation, layout, update behavior, Mobile code, or Shared Core code changed.

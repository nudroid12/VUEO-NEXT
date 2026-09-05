# VUEO Mobile Regression Lock

Status: Mobile v1 regression baseline after Patch 28G.

This document protects already-approved Mobile behaviour while TV work continues. Do not change these contracts during unrelated refactors.

## Startup and profiles

- The startup destination must resolve before Home is allowed to render.
- If `Ask on startup` is enabled, Who's Watching must be the startup destination regardless of profile count.
- A PIN-protected active profile must also force the profile picker.
- Opening Who's Watching from Settings must return the selected user to Settings after profile selection.
- Profile management screens must retain navigation-bar safe spacing.

## Appearance

- Mobile has exactly three dark themes: Charcoal, Midnight and Deep Teal.
- The Settings profile hero remains a neutral translucent light surface, not an accent/lime card.
- My List, Watched and DNA stats remain neutral; DNA does not receive a special accent treatment.

## Main navigation

- Root navigation remains Home, Search, Library and Profile/Settings.
- Opening a catalog details surface or media details must not replace the root navigation state permanently.
- Android back from a Settings subpage returns to its parent before returning Home.

## Content Manager

- Content Manager presentation lives in `mobile/src/main/java/com/vueo/app/ui/VueoContentManager.kt`.
- Addon enabled state and per-catalog enabled state are separate persisted controls.
- Hidden catalogs retain their saved order.
- Home discovery receives both saved catalog order and disabled catalog keys.
- User-facing Content Manager copy stays VUEO-neutral; internal protocol/runtime names may remain technical.

## Provider diagnostics

Expanded diagnostics must preserve useful evidence without restoring metadata noise:

- provider name/version
- captured request context when available
- failure stage/category
- error type/message
- HTTP status when available
- relevant elapsed/timeout timing
- playable source count when useful
- evidence-based likely cause
- sanitized raw technical log

Expand/collapse affordances use chevrons (`>` / `v`), not text arrows.

## Playback path

The Mobile path remains:

Home/Search/Library -> Media Details -> Source Discovery -> Player

Library playback entries must retain their playback/progress context when entering Media Details and the player.

## Change policy

Mobile is feature-locked after this checkpoint. During TV rebuild work, change Mobile only for:

1. confirmed bugs,
2. Shared Core compatibility required by both platforms, or
3. an explicit new product decision.

Prefer small patches. Do not combine startup/profile/player rewrites with unrelated cleanup.

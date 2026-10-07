VUEO Mobile Home + Settings Responsiveness v114
===============================================
Baseline: v113
Scope: Mobile Home + Mobile Settings responsiveness
TV: UNCHANGED
Shared core: UNCHANGED
Gradle build: NOT run (per VUEO project rule)

Problem
-------
Mobile Search and Library felt immediate, while Home and Settings had a small but
repeatable extra delay when changing bottom tabs.

Root cause
----------
Home still did expensive presentation work synchronously during first composition:
- parsed Continue Watching / History from LibraryStore,
- rebuilt personalized Home recommendations / User DNA scoring,
- rebuilt the featured candidate strip from catalog rows.

Settings root also synchronously performed several expensive reads before the first
screen was ready:
- parsed plugin repositories/provider totals,
- parsed Library watchlist/history repeatedly,
- built User DNA synchronously,
- calculated profile statistics before painting the Settings shell.

Fixes
-----
Mobile Home
- Extends MobileHomeRetainedState to keep the already-built featured strip,
  Continue Watching list and recommendation sections warm across tab switches.
- First composition now publishes retained Home presentation immediately.
- Featured candidate extraction runs on Dispatchers.Default.
- Library detail snapshot hydration runs on Dispatchers.IO.
- Home recommendation scoring runs after the first frame on Dispatchers.Default.
- Profile guard prevents retained Continue Watching/recommendations from leaking
  briefly across profile switches.
- Catalog loading, ordering, Home transition animation and Home UI design are unchanged.

Mobile Settings
- Adds MobileSettingsRetainedState owned by VueoApp so the Settings summary remains
  warm after leaving the tab.
- Settings shell can render before plugin/library/DNA summary work completes.
- Library detail snapshot loads on Dispatchers.IO.
- User DNA analysis uses the already-loaded snapshot on Dispatchers.Default instead
  of reparsing LibraryStore synchronously during composition.
- Addon/repository/provider/enhancement summary reads happen off Main.
- First-ever open can briefly show lightweight loading placeholders; later tab returns
  reuse the retained summary while it refreshes in the background.
- Playback/subtitle/source/appearance preference semantics are unchanged.

Not changed
-----------
- TV source is untouched.
- Shared core is untouched.
- No player, resume cursor or Continue Watching policy changes.
- No Next Episode, source discovery or subtitle changes.
- No main-navigation transition timing changes.
- No Home/Settings visual redesign.

Static validation
-----------------
- Kotlin lexical/delimiter balance: PASS on all changed files.
- Duplicate imports: PASS.
- git diff --check equivalent whitespace validation: PASS.
- Patch overlay simulation over v113: PASS.
- Patch root/layout check: PASS (mobile/... + README, no wrapper folder).
- TV directory unchanged against v113: PASS.
- Shared directory unchanged against v113: PASS.
- No Gradle build executed.

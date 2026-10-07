VUEO Kotlin Compile Fix v113
============================
Baseline: v112
Scope: Mobile + TV source compile fixes only
Shared core: UNCHANGED
Gradle build: NOT run (per VUEO project rule)

Source of diagnosis
-------------------
Uploaded CI log: logs_101794020589.zip
The release job reached Kotlin compilation and failed with exactly four source errors:

1. MobileDetails.kt: delegated Compose state could not be smart-cast to UserDnaSnapshot.
2. MobilePlayer.kt: player-start persistence call bound its trailing lambda to
   afterPersist, leaving required parameter block missing.
3. MobilePlayer.kt: periodic persistence call had the same trailing-lambda issue.
4. TvPlayerScreen.kt: periodic persistence call had the same trailing-lambda issue.

Fixes
-----
MobileDetails.kt
- Snapshots detailsDnaSnapshot into a stable local val before checking hasUsefulData.
- Passes that stable local to matchPercent(), allowing Kotlin smart-cast/nullability
  analysis to resolve correctly.
- No DNA behavior or scoring logic changed.

MobilePlayer.kt
- Uses explicit named block = { ... } for the two affected
  enqueueMobilePlayerPersistence calls.
- This prevents Kotlin from assigning a trailing lambda to the optional
  afterPersist parameter.
- Resume cursor/persistence behavior from v112 is unchanged.

TvPlayerScreen.kt
- Uses explicit named block = { ... } for the affected periodic
  enqueueTvPlayerPersistence call.
- TV resume cursor/persistence behavior from v112 is unchanged.

Not changed
-----------
- Shared core is untouched.
- No Continue Watching policy changes.
- No resume semantics changes.
- No source discovery / Next Episode / subtitle changes.
- No UI design or transition changes.
- No player audio behavior changes.

Static validation
-----------------
- All four compiler errors from the uploaded CI log were mapped to changed lines.
- No remaining ambiguous enqueueMobilePlayerPersistence { ... } or
  enqueueTvPlayerPersistence { ... } calls.
- Kotlin delimiter/lexical balance: PASS on all changed files.
- Duplicate imports: PASS.
- git diff --check equivalent whitespace validation: PASS.
- Patch root/layout overlay simulation over v112: PASS.
- Shared directory unchanged against v112: PASS.
- No Gradle build executed.

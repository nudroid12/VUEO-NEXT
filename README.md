# Cast / Production / Network title results tabs
Based on app repo65, changes only TvEntityResultsScreen.kt. This is the title grid after opening an actor/company/network, not the detail-page Cast row.

Selected Newest/Popular uses white background, dark text, no tick. Focus activates sorting immediately. Down is handled explicitly: wait for sort preparation to finish, scroll current grid to first poster, wait for layout and request focus (bounded six frame attempts). Cancel on tab focus changes/hidden screen; failed focus is logged as ENTITY_RESULTS grid_focus_failed. Existing automatic restoration now checks Boolean focus success rather than absence of an exception.

Apply CHANGES.diff with git apply, or copy the full source file. Independent of the prior detail-page Cast row patch; this does not undo it. Existing asynchronous sorting and filtering remain.

Static source checks, git apply --check against repo65 and ZIP CRC passed. Android compilation and on-device DPAD tests not available. Verify Cast, Production and Network: Newest/Popular focus switching; Down after switching; Down after scrolling far; return from poster details.

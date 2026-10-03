Replace the included file from repository root. Based on repo (34) with previous player presentation changes.
TV player controls: fixed short 28dp slide; top enters down/exits up, bottom enters up/exits down; slide and fade synchronized at 240ms enter / 180ms exit with dedicated easing. Other panels retain existing motion.
Existing focus handoff reviewed and preserved: controls reject input when hidden, pending focus jobs cancel on hide, focus returns to root. Layout and button behavior unchanged.
Source delimiter checks and ZIP integrity passed. No build or device test performed.
Verify repeated open/close, Back, auto-hide, D-pad focus, opening Sources/Subtitles and closing mid-animation. Device smoothness still requires testing.

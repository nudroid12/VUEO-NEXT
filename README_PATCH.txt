Apply these repository-relative files over the latest cumulative source.

TV workspace markers: selected white with dark text; other focused items gray. Applies to subtitle language/track selection, audio/source option rows, provider/season pills, episode cards, workspace style/header controls and More controls. Swatches keep actual color, selected check, gray focus ring. Player bottom bar Subs/Audio/Sources/Episodes is unchanged.
Skip intro/recap/credits focus white with dark text. Next Episode Play pill follows the card focus: white with dark icon/text/countdown.
Subtitle defaults in shared settings and mobile/TV style models: 26sp and 8% bottom. Saved explicit/legacy size choices and bottom position are retained; Reset Style uses new defaults. Existing TV migration marker is retained.
Subs workspace TV entry focuses selected subtitle track and scrolls to it; disabled subtitles focus Off. Preferred-language filtering also includes current selected subtitle language so it is accessible. Initial focus requester is shared with the parent entry handoff to prevent a language-focus race.

Validation: static source checks and ZIP integrity only. No local build or device tests.
Device checks: open Subs with selection low in list and with Off; verify initial focus/scroll. Navigate selected and unselected workspace items, test Skip/Play focus, and verify bottom bar unchanged. On new profile/Reset Style verify 26sp and 8%; existing custom settings stay intact.

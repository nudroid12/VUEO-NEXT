VUEO — Mobile player exit: retain the outgoing resume input

INSTALL
Apply after the subtitle rollback package. Upload mobile/ from this ZIP to the VUEO repository root and replace the matching file. Build/install the Mobile APK with the existing workflow. No YML, provider, TV or Library file changes are included. No files need deleting for this patch.

FIX
During Back from Player, MobileDetails clears selectedPlaybackSource, selectedPlaybackVideoId and selectedPlaybackStartPositionMs. AnimatedContent still renders the outgoing Player for the exit animation using retained source/video values, but previously passed the now-reset selectedPlaybackStartPositionMs (0) to it. PlayerScreen includes initialPositionMs in ExoPlayer's remember keys, so leaving a resumed video created another player for the remainder of the exit animation.
MobileDetails now retains the position alongside source/video and passes that retained value to the outgoing player. It updates while the selected source is active, preserving normal source switches and new episode/session startup. Exit still clears the selected state as before.
The supplied log showed this short-lived replacement on three exits (about 0.3 seconds each). TV's player is remembered by bundle.videoId and does not have the same initial-position remember key, so this patch only changes Mobile.

VALIDATION
Reviewed the one-file diff and the exit/reset/source-switch call paths. Kotlin syntax parsing passed. No full APK assembly or device playback was run here. This patch does not address failed media requests or server/network buffering.

DEVICE CHECK
Resume a partially watched movie/episode, then Back to Details. Expect the current player to stop/release without a new PLAYER_RESOURCE created / PLAYBACK_START during the outgoing animation. Reopen to confirm resume remains correct. Also test a normal source switch and next episode startup; both should retain their intended start position.

# TV 43A Player UI Rebuild

Base: 42E Settings Focus Architecture.

## Visual target

The TV player chrome is rebuilt around the supplied reference while preserving VUEO playback behavior.

- Top left shows `Sx Ex • Episode Title`, or the movie title when there is no episode.
- Top right contains Restart, Next Episode when available, and More.
- The previous bottom circular Play, Next, Subs, Audio, Sources, Episodes, More row is removed.
- Bottom progress is full width with current time on the left and total duration on the right.
- Bottom floating pill contains Subs, Audio, Sources, and Episodes when each capability is available.
- Focus treatment is neutral white. Progress continues to use the VUEO accent only as semantic playback progress.
- Player chrome keeps automatic hide while playback is running.

## Remote behavior

- Hidden controls: OK reveals the player and focuses progress. Left and Right seek 10 seconds. Up reveals the contextual prompt when active, otherwise the Restart action. Down reveals the bottom action pill.
- Progress: Left and Right seek 10 seconds. OK toggles Play or Pause. Up moves to Restart. Down moves to the first available bottom action.
- Top actions: Left and Right stay within Restart, Next Episode, and More. Down returns to progress.
- Bottom pill: Left and Right stay within the available pill actions. Up returns to progress. Down is contained.
- OK and Enter commit exactly once on KeyUp for the new chrome actions.
- Back closes an active panel first, then hides controls, then exits on the next Back.
- Closing Subs, Audio, Sources, Episodes, or More restores focus to the action that opened it after the chrome is recomposed.

## Playback behavior preserved

- Media3 and ExoPlayer backend
- Resume and library progress persistence
- 10 second seeking
- Subtitle and audio track selection
- Source switching and recovery
- Episode switching and autoplay next episode
- Skip intro, recap, and ending prompts
- Playback speed and video fit through More
- Hardware media keys

Restart seeks to the beginning and resumes playback.

## Validation

- Only the three TV player source files listed in the patch manifest are changed.
- Kotlin delimiter and parser surface checks show no syntax diagnostics in the changed player files.
- Full Gradle compilation could not start because this environment cannot resolve `services.gradle.org` for the Gradle wrapper download.

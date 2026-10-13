# VUEO TV Settings — Account and Providers polish

Apply this ZIP with the existing root apply-ZIP workflow. No YAML or enclosing repo folder.
Based on VUEO-NEXT-main (48), using the working copy maintained in this conversation.

Scope: TV only. Sidebar categories, sidebar styling and master/detail navigation shell
are byte-identical to the input. No sync backend, account protocol or saved sync scope
changes. All sync categories remain available.

Account: connected account identity, current sync feedback, Sync Now, Sign Out and
sign-out cancellation share the top account panel. Existing sign-out confirmation is
retained. Information-only account/status rows no longer take remote focus. Header
actions have explicit left/right/down navigation and use the existing host focus IDs.
The sign-in/create-account flow remains available for disconnected users.

Settings content: smaller reusable rows, stronger supporting-text readability and
focused-row borders; embedded content receives safe scroll padding. Sidebar untouched.

Providers: compact metrics, shorter labels, quieter surfaces, one horizontal repository
action group for Refresh/toggle/Remove, and operation feedback alongside the selected
repository. Provider format/latency details stay in Diagnostics; the provider list shows
name, supported content types, latest recorded status and toggle. Ready code count is
removed from the summary; it is not a playback health measurement. Status sorting is
cached for the current repository/revision and invalidated when diagnostics opens or
closes. No new network calls or automatic provider tests are introduced.

Validation: all five changed/new Kotlin files parsed with the Kotlin compiler PSI
parser without syntax errors. Sidebar/shell byte comparison passed. Static checks
confirmed retained sync categories/actions and provider Diagnostics navigation.
No Android build, Compose rendering or physical TV D-pad test was available here.
Use the normal GitHub Actions APK build and verify Account header navigation,
sync feedback, sign-out cancellation, repository actions and provider Diagnostics.

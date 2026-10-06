VUEO TV Progress Rail Compile Fix v95

Scope: TV-only compile fix.

Fix:
- Capture BoxWithConstraints maxWidth into an explicit local availableWidth before nested Box scopes.
- Use availableWidth for progress fill width and thumb offset.
- No seek, progress rail, thumb, subtitle, source, focus, or playback behavior changes.

Apply after v94 (or after v92+ when the progress-thumb rail is present).

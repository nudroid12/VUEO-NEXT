# TV 43B1 Player compile fix

GitHub Actions failed in `TvPlayerTracks.kt` because `ForwardingRenderer`, `Renderer`, and `TextOutput` were imported from the wrong Media3 packages.

The project uses Media3 1.11.0. This patch corrects the imports to:

- `androidx.media3.exoplayer.ForwardingRenderer`
- `androidx.media3.exoplayer.Renderer`
- `androidx.media3.exoplayer.text.TextOutput`

No player behaviour or UI logic is changed.

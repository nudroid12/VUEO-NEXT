# TV 44B Subtitle Transparency and Focus

## Approved scope

- Keep the existing VUEO three-column subtitle layout.
- Reduce only the full-screen scrim opacity so video and rendered subtitles stay
  visible in their real positions.
- Do not add Float mode or move the rendered subtitle.
- Preserve track selection, styling, delay and persistence behaviour.

## D-pad correction

- Opening Subtitles requests focus on the selected language, or None when
  subtitles are disabled.
- Focus request retries across initial composition frames instead of marking a
  failed request as completed.
- If the panel opened without receiving focus, the next directional, OK or
  Enter input transfers focus into the same entry row.
- Once focus is inside the workspace, existing per-row navigation remains
  authoritative.

## Validation

Source diff and archive integrity checks are required locally. GitHub Actions
remains the canonical Android build confirmation when dependencies are not
available in the handoff environment.

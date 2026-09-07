# TV 41A Final UI Polish

Applies on top of TV 40A.

## Player overlay corrections

- Transport controls are hidden while Subtitle, Audio, Sources or Episodes panels are open.
- Subtitle and Audio overlay width is reduced substantially for the real TV density seen in testing.
- Source and Episodes panels are reduced to a compact, overscan-safe right panel.
- Episode thumbnails, season chips, typography and focus borders are tightened.
- Raw ISO episode dates such as `2022-02-04T11:00:00.000Z` are formatted as `Feb 4, 2022` in Player and Details.
- Selected options use a small accent indicator instead of a large selected treatment.
- Player title, episode metadata, progress rail and circular controls are slightly reduced for better visual balance.
- Skip/next prompts no longer compete with modal panels.

## Regression lock

No Home, Sidebar, Source, mobile or backend files are included in this patch.
39A Details layout stays locked, with only episode date display formatting changed.

## Reference

The final overlay proportions and hierarchy were checked against the supplied NuvioTV 0.8.6-beta player sources, especially `PlayerOverlayScaffold`, `SubtitleSelectionOverlay`, `AudioSelectionOverlay`, `StreamSourcesSidePanel` and `EpisodesSidePanel`, then adjusted for the larger effective density visible on the test TV.

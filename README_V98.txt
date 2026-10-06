VUEO v98 — Generic Dual-Layer Subtitle Gap

Scope:
- TV + Mobile subtitle renderer only.
- No shared-core change.
- Supersedes the v97 measured-gap renderer; if v97 is not installed, v98 can be applied directly after v96 because these are full overlay files.

Changes:
1. Subtitle spacing no longer depends only on commentary classification.
2. If two or more lower-screen text cues are active simultaneously, VUEO splits them into lower + upper display layers and applies the measured 28dp collision gap.
3. Tagged commentary still uses Commentary Size.
4. A generic second/translated/addon subtitle uses the normal Font Size, while still receiving the same collision gap.
5. The lower layer height is measured with the actual configured font and available player width, including wrapping, before positioning the upper layer.
6. Single active subtitle remains at the configured Bottom Position.
7. Commentary toggle behavior is preserved.

Not changed:
- subtitle timing/delay
- bottom-position setting
- seek/playback
- source discovery
- next episode
- player controls

Validation:
- static delimiter check passed
- patch marker / whitespace checks passed
- no Gradle build performed

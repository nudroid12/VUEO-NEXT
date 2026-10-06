VUEO v102 - Global Parenthetical Commentary Rule + 20dp Gap

Scope: TV + Mobile subtitle rendering only. No shared-core refactor.

Changes
1. Removed the old English/authored commentary color/style-marker classifier (#FFFFCC ForegroundColorSpan).
2. v101 full-parentheses rule is now the only commentary classifier for both TV and Mobile, regardless of subtitle language/source.
3. Classification still applies when 2+ lower subtitle cues are active simultaneously:
   - (Pintu ditutup) => commentary
   - (Mengeluh) Saya penat hari ini. => normal subtitle
   - Saya penat hari ini. (Mengeluh) => normal subtitle
4. Generic cue stacking no longer inspects color/style metadata, preventing old English markers from overriding v101 classification.
5. Commentary gap reduced from 28dp to 20dp; measured lower-subtitle height/wrapping logic remains unchanged.
6. Commentary Size, toggle, Bottom Position, subtitle delay and generic dual-layer spacing remain intact.

Validation
- Static/diff checks only; Gradle build intentionally not run per project instruction.
- Confirmed no ForegroundColorSpan/#FFFFCC commentary detection remains in patched subtitle files.

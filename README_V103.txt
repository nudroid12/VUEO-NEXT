VUEO v103 - Frame-Continuity Motion Fix

Base: v102 cumulative state / supersedes v100 motion helpers.

Scope
-----
TV + Mobile motion helpers only. No shared-core changes.

Changes
-------
1. Full-page transitions are now near-sequential fade-throughs.
   - Outgoing page fades first.
   - Incoming page starts only after outgoing content is already faint.
   - Removes the clearly readable double-page overlap seen in v100.

2. Removed outgoing full-screen scale animation.
   - Only incoming page receives a tiny micro-scale (0.996/0.997 -> 1.0).
   - Reduces simultaneous full-screen transform work and avoids the visible
     'catch' / jerk at the handoff on poster-heavy screens.

3. Shorter, cleaner motion timings.
   - Mobile normal screen: 96ms exit, incoming starts ~72ms, 165-180ms in.
   - Mobile Home return: 92ms exit, incoming starts ~80ms.
   - TV screen: 96ms exit, incoming starts ~72ms.

4. Player fade-only timings are unchanged from v100. No video-surface scale was introduced.

5. Panel/dialog micro-scale behavior remains intentionally separate from
   full-screen navigation.

Validation
----------
- Static Kotlin structure/import check performed.
- Checked patch paths and diff scope.
- No Gradle build run, per project instruction.

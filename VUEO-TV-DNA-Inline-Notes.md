# TV Details DNA inline correction

Replaces the previous TV DNA badge patch. DNA appears after the ratings on the same row,
using the exact mobile DNA drawable and the same icon / DNA / percentage structure.
Uses existing nullable state.dnaMatch; follows current DNA settings and data readiness.
Preserves Favorites heart. No extra API request, storage access or DNA calculation.

Apply this ZIP after the earlier DNA patch, or use this patch alone.
Repository-root paths, no YAML. Includes TV hero and the DNA drawable.
Static checks and archive integrity passed. Android build and TV rendering not run.

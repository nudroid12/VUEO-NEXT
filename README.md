# TV source status — wrap all fields as units
Replaces the previous adaptive-wrap patch. Only TvSourcePresentation.kt changes. Every bullet-separated field (Searching, Addons 1/1, Providers 22/40, 15 found, 28/135s) is indivisible. Greedy line packing measures complete candidates with the rendered style and layout width, including font scale. All fields stay on one line when they fit; whole fields move to following lines when necessary. Separators are inserted within each resulting line, never at line boundaries. A single field wider than the entire viewport ellipsizes instead of breaking internally.

Rankings, source ordering, discovery and timer behavior unchanged. Copy included source, or apply CHANGES.diff against repo65. AFTER_PREVIOUS_PATCH.diff is for the previous Source Status Adaptive Wrap patch; use only one diff.

Validation: scoped diff checks, both patch baselines and ZIP CRC. Android build and TV visual verification not run.

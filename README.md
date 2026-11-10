# TV source status adaptive wrapping
Based on repo65. Only TvSourcePresentation.kt changes. Measure the complete status using actual layout width and inherited font/style. If it fits, keep one line. If not, move the trailing found count plus elapsed/budget together to a separate non-wrapping line. Also handles budget-only progress when found is omitted. On unusually narrow layouts, header can use two lines and footer ellipsizes as one unit.

No ranking, discovery, timeout, timer or provider-tab changes. Apply CHANGES.diff with git apply, or copy included source file. Static pattern/source checks, patch applicability and ZIP CRC passed. Android build and on-device layout validation not run.

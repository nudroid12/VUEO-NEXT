Apply the included files at repository root. Based on VUEO repo (34).
Mobile Search: show Searching/Preparing search until the current query+mode+content version completes; prevent premature No matches; ignore obsolete query responses. Actor partial UI updates use the effect Main dispatcher and cancellation lifetime.
Shared title search: forward provider partial results via a conflated channel, rank on Default dispatcher, publish on caller context; propagate cancellation and preserve final merge. Shared change also benefits TV callers of SearchOrchestrator.
Static source checks and ZIP integrity check passed. No build or device tests performed.
Device checks: type quickly, change title/actor mode, clear query, leave Search mid-request, test slow providers and genuine empty results.

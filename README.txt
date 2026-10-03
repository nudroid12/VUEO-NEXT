Apply after VUEO_TV_Details_Dpad_MAL_Rating_Fix_Patch.zip. Replace TvRuntime.kt at the included path.

Fix: TvRuntime had a second rating allowlist with else=false. The earlier patch accepted MAL in the shared parser and rendered its badge but omitted this runtime gate. The runtime now passes canonical myanimelist ratings through. Existing rating toggles and the global MDBList ratings/key check are preserved. MAL is included when global MDBList ratings are enabled and the API returns a valid value.

Validation: static checks that parser -> runtime -> TV badge all accept myanimelist; confirmed the single added runtime branch is the only source change; ZIP integrity. No build or device test performed.

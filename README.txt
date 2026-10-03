Apply after VUEO_TV_Details_Hero_Episodes_Patch.zip. Replace the three source files at their matching paths.

TV Details remote navigation: intercept Up/Down at hero, season and episode boundaries; animate to reveal the target lazy item, await layout frames, then request focus. Left/Right and activation retain existing behavior. Existing hero dimensions and episode cards are retained.

Ratings: accept MDBList myanimelist ratings (including mal/my_anime_list aliases), label MAL, decimal score, blue MAL badge in TV hero. Existing sources/order remain unchanged. No new network request, API key or invented score. MAL requires ratings enabled, the existing MDBList key, and a returned MAL value for this title. The shared parser also makes the value available to mobile.

Verification: source checks for scroll-before-focus, navigation boundaries, rating allowlist/label, delimiter balance, and ZIP integrity. No local build or device test performed. On TV test Play -> Down -> Season -> Down -> Episode; Up in reverse; Left/Right across episodes; open the anime again and check MAL when supplied.

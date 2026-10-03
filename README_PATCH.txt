Extract at repository root, replacing included TV files. Apply to the current cumulative session repository.
Pause backdrop adds synopsis below episode details (episode overview first, then media description; hidden if neither available). Maximum 3 lines, 15sp, 20sp line-height.
Top-right current clock: 30sp; End at below: 14sp. Uses device local timezone and system 12/24-hour preference. Clock refreshed each second only while pause backdrop is visible. End at = current wall time + remaining video duration / playback speed, assuming resume now without future buffering. Hidden if duration unavailable.
Retains 8-second pause timer, title/episode, elapsed/duration, remaining video minutes, resume hint and root focus behavior. No network requests for missing synopsis.
Validation: source/call-site/delimiter review, simple speed arithmetic sanity check and ZIP integrity passed. No local build or device test performed.
Device checks: synopsis for episode/movie and missing metadata; clock 12/24-hour preference; End at at 1x/2x; pause time shifts End at forward; resume dismisses backdrop.

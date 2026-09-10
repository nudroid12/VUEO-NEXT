# TV 45 Motion Polish

TV 45 consolidates the Android TV motion language around fast D-pad response and restrained cinematic transitions.

## Timing

| Motion | Enter | Exit |
| --- | ---: | ---: |
| Focus | 120ms | 90ms |
| Element and chrome | 160ms | 110ms |
| Panel | 190ms | 110ms |
| Screen | 250ms | 130ms |
| Player route | 180ms | 90ms |
| Hero and backdrop | 170ms to 270ms | 170ms to 270ms |

## Behaviour locks

- Do not use bounce or spring motion.
- Do not scale the video during Player transitions.
- Keep focus borders immediate while scale settles subtly.
- Keep the subtitle workspace transparent and use fade-only motion.
- Do not move the rendered subtitle when the subtitle workspace opens.
- Use `TvMotion.EaseOut` for entry and focus, and `TvMotion.EaseInOut` for exit.

## Validation

The changed Kotlin sources were checked by diff inspection and whitespace validation. No local Gradle build was run.

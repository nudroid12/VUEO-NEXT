# TV 46 - Resolver compatibility

This patch updates the JavaScript provider bridge on top of the latest uploaded repository.

## Provider HTTP session

Each provider execution receives its own short-lived OkHttp cookie jar. A provider can now make a login, consent, anti-bot, or token request and use the resulting cookie on later fetch and axios calls. The session is discarded after that provider execution. If the final stream URL matches a stored cookie, the cookie is added to the stream headers unless the provider already supplied one.

## Output shapes

The bridge accepts the common provider return forms below:

- `Stream[]`
- `{ streams: Stream[] }`
- `{ sources: Stream[] }`
- `{ results: Stream[] }`
- `{ items: Stream[] }`
- `{ data: Stream[] }`
- `{ links: Stream[] }`
- `{ stream: Stream }`, `{ result: Stream }`, or a single stream object

## Metadata and aliases

The resolver accepts direct URL aliases, torrent info hash aliases, file index, quality, codec, HDR, audio, language, byte size, rank boost, and request/proxy headers including nested `behaviorHints.proxyHeaders`.

The existing HTTPS playback policy remains unchanged. No local build was run.

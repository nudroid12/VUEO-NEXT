# TV 47 - Universal provider runtime

TV 47 completes the general compatibility layer used by current and future JavaScript providers. The runtime does not identify or optimise for any specific provider.

## Network and lifecycle

Native HTTP calls now support bounded text and binary bodies, request-specific timeouts, provider-scoped cookies and active request cancellation. Provider termination cancels remaining calls. DNS protection is applied to every destination, including redirects, and HTTPS downgrade redirects are disabled.

## Web-compatible APIs

The runtime exposes Headers, Request, Response, Blob, File, FormData, AbortController and AbortSignal. Fetch responses support text, JSON, ArrayBuffer, bytes, Blob and cloning. Multipart and binary bodies are encoded only when used.

## Axios-compatible APIs

Axios supports all common HTTP methods, base URL handling, query parameters, custom parameter serialisation, basic authentication, response types, transforms, instances, defaults, interceptors, timeout, AbortSignal and CancelToken.

## Compatibility modules

Providers can load axios, node-fetch, cross-fetch, undici, form-data, buffer, cheerio and crypto-js through the existing runtime require bridge.

## Resource limits

Request and response bodies remain limited to 4 MiB. Direct audio and video bodies are skipped. Custom request timeouts are limited to 30 seconds. These limits prevent a provider from consuming unbounded memory or keeping the runtime alive indefinitely.

The metadata addon path, UI and playback presentation are unchanged. No Android build was run locally.

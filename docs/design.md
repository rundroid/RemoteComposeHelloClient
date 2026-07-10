# RemoteComposeHelloClient

A minimal single-Activity Compose app that fetches a RemoteCompose binary
document from the `remote-compose-hello` Spring Boot server and renders it
with `RemoteComposePlayer`. Companion client to
[`remote-compose-hello`](../../../Spring/remote-compose-hello).

## Requirements

- `remote-compose-hello` running locally (`./gradlew bootRun`) before launching
  this app.
- This app must run in an **emulator**, not a physical device — it talks to
  `http://10.0.2.2:8080`, the emulator's alias for the host machine's
  localhost. A physical device would need the host's real LAN IP instead.
- `minSdk 26` — the floor set by `androidx.compose.remote:remote-player-view`.

## Dependencies

- `androidx.compose.remote:remote-player-view:1.0.0-alpha14`
- `androidx.compose.remote:remote-player-core:1.0.0-alpha14`
- `androidx.compose.remote:remote-core:1.0.0-alpha14` — declared explicitly
  because the player libraries pull it in as `implementation`, not `api`, so
  `CoreDocument`/`RemoteContextActions` aren't visible transitively.
- `com.squareup.okhttp3:okhttp:4.12.0` for the HTTP fetch.

Resolved from Google's Maven repo (already configured by the AGP 9 template's
`settings.gradle.kts`).

## Architecture

- `HelloDocumentClient.kt` — a suspend function, `fetchHelloDocument()`, that
  does a synchronous OkHttp GET on `Dispatchers.IO` and returns the raw bytes.
- `MainActivity.kt` — a `ComponentActivity` with a `HelloDocumentScreen`
  composable:
  - `DocumentState` (`Loading` / `Error` / `Ready`) drives the UI; fetched via
    `LaunchedEffect` keyed on a retry counter.
  - On `Ready`, `RemoteComposeDocumentView` wraps a `RemoteComposePlayer`
    (a `FrameLayout`, not a composable) via `AndroidView`. `setDocument(bytes)`
    parses the raw bytes directly — no manual buffer/document construction
    needed on the client.
  - Before re-registering the click listener, `player.document.document
    .clearActionCallbacks()` is called first: `update` re-runs on every
    recomposition, and without clearing, each recomposition would stack
    another `addIdActionListener`, firing the Toast multiple times per tap.
    This mirrors Google's own `RemoteDocumentPlayer` reference composable.
  - On `Error` (server unreachable, non-200, etc.), shows the error message
    and a Retry button rather than crashing.

## Manifest

- `<uses-permission android:name="android.permission.INTERNET"/>` — required
  for OkHttp to make any request at all.
- `res/xml/network_security_config.xml` scopes cleartext HTTP to `10.0.2.2`
  only (not a blanket `usesCleartextTraffic="true"`), since `http://` is
  blocked by default on API 28+ and `10.0.2.2:8080` is plain HTTP.

## Verified end-to-end

Built, installed, and run on the `Medium_Phone_API_36.1` emulator against a
live `remote-compose-hello` instance:
1. Screenshot confirmed "Hello world" text + purple "Click me" button +
   blue "Open Link" button render.
2. Tapping "Click me" produced a Toast reading "Received action id: 1" —
   confirming the id chosen server-side in `CLICK_ME_ACTION_ID` round-trips
   through the RemoteCompose document and `addIdActionListener` correctly.
3. Tapping "Open Link" opened `www.wellsfargo.com` (the real Wells Fargo
   homepage rendered) in a Chrome Custom Tab (`CustomTabActivity` confirmed
   in focus) — confirming `OPEN_LINK_ACTION_ID`'s metadata (the URL)
   round-trips through the document and `openUrlInBrowser` correctly.

## Known limitations (sample-app scope)

- No retry/backoff policy beyond the manual Retry button.
- `CLICK_ME_ACTION_ID` still only shows a demonstration Toast — a real app
  would replace it with actual behavior (network calls, navigation, etc.).
  `OPEN_LINK_ACTION_ID` is the first action id with real behavior: it opens
  its metadata URL in a Custom Tab (see `openUrlInBrowser` in
  `MainActivity.kt`).
- No URL validation beyond scheme-normalization (`https://` is prepended
  when missing) — a malformed or unexpected metadata string would be passed
  to Custom Tabs as-is.

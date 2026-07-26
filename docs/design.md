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

## News screen

A second screen, `NewsActivity`, fetches `remote-compose-hello`'s
`GET /api/news` document (a horizontally scrollable carousel of news Cards,
each with an image, title, and description) and renders it. `MainActivity`
gained a "View News" button that launches it via an explicit `Intent`.

`HelloDocumentScreen` and the new news screen now share
`RemoteDocumentScreen.kt` (`RemoteDocumentScreen` composable + `DocumentState`),
extracted from what used to be `MainActivity`-only code. `NewsDocumentClient.kt`
mirrors `HelloDocumentClient.kt`'s OkHttp-on-`Dispatchers.IO` pattern exactly,
against `http://10.0.2.2:8080/api/news`.

### Article images: what it actually took to make URL-backed bitmaps work

The backend embeds each article's image as a URL (`writer.addBitmapUrl`), not
raw bytes — the player is expected to fetch and decode it itself. Getting
this to actually work end-to-end (built, installed, and driven on the
`Medium_Phone_API_36.1` emulator against a live backend with a real
`NEWSAPI_API_KEY`) took three fixes, two of which are done and one of which
is an open follow-up:

1. **Main-thread network fix (done).** `AndroidBitmapLoader.loadBitmap` is a
   blocking, un-threaded `URL.openStream()` call, invoked synchronously
   wherever `BitmapData.apply()` runs. `RemoteComposeDocumentView` calls
   `player.prepareDocument(RemoteDocument(bytes))` on `Dispatchers.IO` (which
   is where the network fetches happen) and only
   `player.setPreparedDocument(prepared)` on the main thread.

2. **`Limits.ENABLE_IMAGE_URLS` gate (done).** Even with the threading fixed,
   the very first attempt crashed with `RuntimeException: URL image not
   supported [45]`, thrown from `BitmapData.read()` during document parsing
   -- before any network I/O happens at all. `androidx.compose.remote.core.Limits.ENABLE_IMAGE_URLS`
   defaults to `false`; it's a deliberate safety gate against a document
   making the app fetch arbitrary attacker-supplied URLs. `RemoteComposeDocumentView`
   now sets `Limits.ENABLE_IMAGE_URLS = true` before constructing
   `RemoteDocument` (accepted here since this app only ever loads documents
   from a server we control).

3. **Backend: declared bitmap dimensions (done).** With the gate open, the
   next attempt crashed with `RuntimeException: dimensions don't match
   980x653 vs 1x1` from `RemoteBitmapDecoder.checkBounds`. The backend's
   `NewsCarouselDocument.kt` called the 1-argument `writer.addBitmapUrl(url)`
   overload, which defaults the declared width/height to `1x1`; the client
   enforces that a decoded image's actual pixel dimensions never exceed the
   *declared* ones (a guard against a document under-declaring size to
   smuggle an oversized bitmap). Fixed by declaring a generous
   `MAX_DECLARED_IMAGE_DIMENSION = 4096` bound instead (backend commit on
   `worktree-news-carousel`) -- the server never downloads the image, so it
   can't declare the real size, only a safe upper bound.

4. **Total bitmap memory budget (NOT resolved -- open follow-up).** After
   fix 3, the news screen still doesn't reliably render real cards: a
   real run showed one card's image slot replaced with the player's own
   "memory"-related warning overlay (partial text: "...map memory 1152M..."),
   and the activity was subsequently torn down and the app fell back to
   `MainActivity` (no Java stack trace this time -- looked like a timeout/ANR
   rather than a crash). `RemoteComposeView.java` compares
   `document.bitmapMemory()` (accumulated per-bitmap `height * width * 4`)
   against `Limits.MAX_BITMAP_MEMORY`, which defaults to a conservative `20 *
   1024 * 1024` (20MB) -- and up to 10 real article photos, even at modest
   sizes, plausibly exceed that budget in one document. This wasn't fully
   root-caused or fixed in this session. Likely next step: call
   `player.setMaxBitmapMemory(...)` (or reduce the number of images loaded at
   once) and re-verify with a real `NEWSAPI_API_KEY` end to end.

**Net result:** the Hello screen (verified unaffected -- text, Click me
Toast, and the new View News button all still work after the
`RemoteDocumentScreen` extraction) and app navigation (View News ->
`NewsActivity`) both work. The news screen itself does not yet reliably
render real article images end-to-end; fixes 1-3 were necessary and are
committed, but fix 4 is required before this is fully working.

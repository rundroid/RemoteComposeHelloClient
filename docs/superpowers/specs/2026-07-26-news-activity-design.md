# News Activity design

## Purpose

Add a second screen to `RemoteComposeHelloClient` that fetches the
`remote-compose-hello` backend's new `GET /api/news` RemoteCompose document
(a horizontally scrollable carousel of news Cards, each with an image, title,
and description) and renders it, matching the existing Hello-document screen's
pattern. `MainActivity` gets a "View News" button that launches this new
screen.

## Architecture & data flow

```
MainActivity
  "View News" Button -> Intent(context, NewsActivity::class.java) -> startActivity

NewsActivity
  onCreate -> setContent { RemoteDocumentScreen(fetch = ::fetchNewsDocument) }

RemoteDocumentScreen(fetch, onAction)      [new, shared file: RemoteDocumentScreen.kt]
  LaunchedEffect(loadAttempt) -> DocumentState (Loading / Error / Ready(bytes))
  on Ready -> RemoteComposeDocumentView(bytes, onAction)

RemoteComposeDocumentView(documentBytes, onAction)
  val player = remember { RemoteComposePlayer(context) }
  LaunchedEffect(documentBytes):
    prepared = withContext(Dispatchers.IO) { player.prepareDocument(RemoteDocument(documentBytes)) }
    player.setPreparedDocument(prepared)          // main thread, cheap
    player.document.document.clearActionCallbacks()
    player.document.document.addIdActionListener { id, metadata -> onAction(id, metadata) }
  AndroidView(factory = { player })
```

`MainActivity`'s existing `HelloDocumentScreen` becomes a thin wrapper around
the same shared `RemoteDocumentScreen`, passing `fetchHelloDocument` and its
existing `CLICK_ME_ACTION_ID`/`OPEN_LINK_ACTION_ID` handling as `onAction`.
`NewsActivity` passes `fetchNewsDocument` and no `onAction` (the news cards
carry no `HostAction`, confirmed from the backend's `NewsCarouselDocument.kt`
-- there is nothing for a listener to handle).

## The image-loading finding (why this isn't just a copy-paste of the Hello screen)

The backend's news document embeds each article's image as a **URL**
(`writer.addBitmapUrl(url)`), not raw bytes -- the player is expected to
resolve and decode it itself. Reading the actual `remote-player-core`/
`remote-player-view` 1.0.0-alpha15 sources (already in this project's Gradle
cache) confirms:

- `AndroidRemoteContext`'s public constructors already wire in a real,
  working `AndroidBitmapLoader` (`URL.openStream()`) by default -- URL-backed
  images are supported out of the box, no extra configuration needed.
- However, that fetch is **synchronous and un-threaded**: `BitmapData.apply()`
  -> `AndroidRemoteContext.loadBitmap()` -> `RemoteBitmapDecoder.decodeBitmap()`
  -> `AndroidBitmapLoader.loadBitmap(url)` is a plain blocking call, with no
  executor or background dispatch anywhere in that path. Calling
  `player.setDocument(bytes)` directly (mirroring the existing Hello screen)
  would run this on whatever thread renders the document -- the Compose main
  thread -- and would very likely crash with `NetworkOnMainThreadException`
  the first time a card's image is resolved (`targetSdk = 36` enforces this).
- The library ships a two-step API for exactly this problem:
  `player.prepareDocument(RemoteDocument): PreparedDocument?` synchronously
  resolves every embedded bitmap (including the network fetches, verified by
  reading `RemotePreparedDocument`'s constructor, which iterates
  `doc.getDocument().getBitmapDataSet()` and calls `.apply(...)` on each) --
  call this off the main thread. `player.setPreparedDocument(PreparedDocument)`
  then hands the pre-resolved data to the player and calls the normal
  `setDocument(...)` internally -- cheap, no I/O, safe on the main thread.
  Both methods are `@RestrictTo(LIBRARY_GROUP)`, the same annotation already
  covering every other RemoteCompose API this project depends on (`setDocument`
  itself, the whole `remote-creation-*` writer API on the backend) --
  accepting that trade-off is already this project's established norm, not a
  new category of risk.

`RemoteComposeDocumentView`'s `LaunchedEffect(documentBytes)` (not
`AndroidView`'s `update` block) is what makes this both correct and cheap:
it only re-runs when `documentBytes` actually changes (a new `ByteArray`
instance, e.g. from a fresh fetch or Retry), not on every unrelated
recomposition -- so the image fetches happen once per distinct document, not
repeatedly.

## Files

- **Create** `app/src/main/java/com/example/remotecomposehelloclient/RemoteDocumentScreen.kt`
  -- `DocumentState` (moved from `MainActivity.kt`, unchanged), the public
  `RemoteDocumentScreen` composable, and the private `RemoteComposeDocumentView`
  described above.
- **Modify** `app/src/main/java/com/example/remotecomposehelloclient/MainActivity.kt`
  -- `HelloDocumentScreen` delegates to `RemoteDocumentScreen`; `CLICK_ME_ACTION_ID`/
  `OPEN_LINK_ACTION_ID` handling and `openUrlInBrowser` are unchanged, just
  moved into the `onAction` lambda. `onCreate`'s `setContent` adds a "View
  News" `Button` above the existing screen content that starts `NewsActivity`
  via an explicit `Intent`.
- **Create** `app/src/main/java/com/example/remotecomposehelloclient/NewsActivity.kt`
  -- a second `ComponentActivity`, same theme/edge-to-edge setup as
  `MainActivity`, `setContent { RemoteDocumentScreen(fetch = ::fetchNewsDocument) }`.
- **Create** `app/src/main/java/com/example/remotecomposehelloclient/NewsDocumentClient.kt`
  -- mirrors `HelloDocumentClient.kt` exactly: its own `private val client =
  OkHttpClient()`, `NEWS_ENDPOINT = "http://10.0.2.2:8080/api/news"`,
  `suspend fun fetchNewsDocument(): ByteArray`. A non-2xx response (including
  the backend's 502-on-upstream-failure) becomes an `IOException`, surfaced
  by the existing Error/Retry UI -- no news-specific error handling needed.
- **Modify** `app/src/main/AndroidManifest.xml` -- registers `NewsActivity`
  (`android:exported="false"`, no intent-filter -- it's only ever launched via
  explicit `Intent` from `MainActivity`).

No new Gradle dependencies, no manifest permission changes, no
`network_security_config.xml` changes (article images are HTTPS; only
`10.0.2.2` cleartext is scoped there and that's unaffected).

## Out of scope

- Any change to the backend (`remote-compose-hello`) -- this is client-only.
- Making news cards tappable -- the backend attaches no `HostAction` to them,
  so there's nothing for `onAction` to receive.
- A shared/pooled `OkHttpClient` across `HelloDocumentClient.kt` and
  `NewsDocumentClient.kt` -- mirroring the existing one-client-per-file
  pattern is simpler and consistent; not worth a cross-file refactor for two
  demo endpoints.
- Any behavior change to the existing Hello screen beyond the mechanical
  extraction into the shared composable (fixing the same
  prepare/setPreparedDocument threading for it too, even though its document
  has no images today, keeps one correct implementation instead of two).

## Verification

Build, install, and run on an emulator against a live `remote-compose-hello`
instance (`./gradlew bootRun`, with `NEWSAPI_API_KEY` set to a real key so
`/api/news` returns real articles): confirm the Hello screen still works
unchanged, tapping "View News" opens `NewsActivity`, the news cards render
with images and are horizontally scrollable, and no
`NetworkOnMainThreadException` (or any other crash) occurs. Document the
result in `docs/design.md`'s existing "Verified end-to-end" style.

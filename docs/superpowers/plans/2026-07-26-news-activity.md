# News Activity Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a second screen (`NewsActivity`) that fetches the backend's `GET /api/news` RemoteCompose document and renders it, reached via a "View News" button on `MainActivity`.

**Architecture:** Extract the existing Hello-screen's fetch/render logic into a shared `RemoteDocumentScreen` composable (fixing a real main-thread network risk in the same move), then build the News screen on top of it. `NewsDocumentClient.kt` mirrors `HelloDocumentClient.kt` exactly.

**Tech Stack:** Kotlin, Jetpack Compose, `androidx.compose.remote:remote-player-*:1.0.0-alpha15`, OkHttp, coroutines. No new Gradle dependencies.

## Global Constraints

From `docs/superpowers/specs/2026-07-26-news-activity-design.md` — every task below implicitly includes them.

- Article images are URL-backed (`ENCODING_URL` bitmaps). Resolving them calls a **blocking, un-threaded** network fetch (`AndroidBitmapLoader.loadBitmap` → `URL.openStream()`) the moment `BitmapData.apply()` runs. Any code that sets a document containing these images **must** go through `player.prepareDocument(RemoteDocument)` on `Dispatchers.IO` first, then `player.setPreparedDocument(prepared)` on the main thread — never call `player.setDocument(bytes)` directly for a document that may contain URL-backed images.
- `RemoteComposeDocumentView`'s image-prep work must be keyed to `LaunchedEffect(documentBytes)`, not `AndroidView`'s `update` block, so it runs once per distinct document rather than on every unrelated recomposition (avoids redundant re-fetching of the same images).
- No new Gradle dependencies, no manifest permission changes, no `network_security_config.xml` changes (article images are HTTPS; only `10.0.2.2` cleartext is scoped there).
- News cards carry no `HostAction` from the backend — `NewsActivity`'s `RemoteDocumentScreen` call needs no `onAction` (the default no-op is correct).
- `NewsDocumentClient.kt` mirrors `HelloDocumentClient.kt`'s pattern exactly: its own `private val client = OkHttpClient()` (no shared/pooled client across the two files — out of scope per the spec).
- This app talks to `10.0.2.2:8080` and must run in the **`Medium_Phone_API_36.1` emulator**, not a physical device — a real device (`10BG1D1EGA009YS` / model `I2404`) was already attached via `adb` when this plan was written; target the emulator explicitly (`-s <emulator-serial>` / `ANDROID_SERIAL`) so builds don't install on the wrong target.
- The backend (`remote-compose-hello`, `GET /api/news`) needs `NEWSAPI_API_KEY` set to a real key for cards to actually contain real articles/images — at plan-writing time no key was configured, so `/api/news` returns `502` and the News screen's Error/Retry path is what's exercised instead. If a key becomes available before Task 3, verify the real image-rendering path too; if not, verifying the correct Error/Retry behavior is what Task 3 can do, and full image verification is a documented follow-up.

---

### Task 1: Extract shared `RemoteDocumentScreen`, refactor `MainActivity` to use it

**Files:**
- Create: `app/src/main/java/com/example/remotecomposehelloclient/RemoteDocumentScreen.kt`
- Modify: `app/src/main/java/com/example/remotecomposehelloclient/MainActivity.kt`

**Interfaces:**
- Produces: `sealed interface DocumentState` (`Loading` / `Error(message: String)` / `Ready(bytes: ByteArray)`), moved verbatim from `MainActivity.kt`.
- Produces: `fun RemoteDocumentScreen(fetch: suspend () -> ByteArray, onAction: (id: Int, metadata: String?) -> Unit = { _, _ -> })` — a `@Composable`, consumed by Task 2 (`NewsActivity`) and by this task's own refactored `MainActivity`.

This task is a pure refactor — `MainActivity`'s behavior must be unchanged (same Hello-world screen, same Click-me/Open-Link handling). There's no `NewsActivity` reference yet in this task; that comes in Task 2.

- [ ] **Step 1: Create the shared screen**

`app/src/main/java/com/example/remotecomposehelloclient/RemoteDocumentScreen.kt`:

```kotlin
package com.example.remotecomposehelloclient

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.remote.player.core.RemoteDocument
import androidx.compose.remote.player.view.RemoteComposePlayer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

sealed interface DocumentState {
    data object Loading : DocumentState

    data class Error(val message: String) : DocumentState

    data class Ready(val bytes: ByteArray) : DocumentState {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as Ready

            if (!bytes.contentEquals(other.bytes)) return false

            return true
        }

        override fun hashCode(): Int {
            return bytes.contentHashCode()
        }
    }
}

/**
 * Fetches a RemoteCompose document via [fetch] and renders it, showing a
 * loading spinner while fetching and an error message with a Retry button
 * on failure. [onAction] receives action ids (and optional string metadata)
 * fired by tapping components in the rendered document.
 */
@Composable
fun RemoteDocumentScreen(
    fetch: suspend () -> ByteArray,
    onAction: (id: Int, metadata: String?) -> Unit = { _, _ -> },
) {
    var state by remember { mutableStateOf<DocumentState>(DocumentState.Loading) }
    var loadAttempt by remember { mutableIntStateOf(0) }

    LaunchedEffect(loadAttempt) {
        state = DocumentState.Loading
        state =
            try {
                DocumentState.Ready(fetch())
            } catch (t: Exception) {
                DocumentState.Error(t.message ?: "Failed to load document")
            }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        when (val current = state) {
            is DocumentState.Loading -> CircularProgressIndicator()
            is DocumentState.Error -> {
                Text("Couldn't reach the server:")
                Text(current.message)
                Button(onClick = { loadAttempt++ }) { Text("Retry") }
            }
            is DocumentState.Ready -> RemoteComposeDocumentView(current.bytes, onAction)
        }
    }
}

/**
 * Renders RemoteCompose document bytes via [RemoteComposePlayer]. Article
 * images embedded as URLs (`ENCODING_URL` bitmaps) are resolved by
 * `AndroidBitmapLoader` doing a blocking network fetch as part of
 * [RemoteComposePlayer.prepareDocument] -- so preparation runs on
 * [Dispatchers.IO] and only [RemoteComposePlayer.setPreparedDocument] (cheap,
 * no I/O) runs on the main thread. Keyed on [documentBytes] so this only
 * re-runs when the document actually changes, not on every recomposition.
 */
@Composable
private fun RemoteComposeDocumentView(documentBytes: ByteArray, onAction: (Int, String?) -> Unit) {
    val context = LocalContext.current
    val player = remember { RemoteComposePlayer(context) }

    LaunchedEffect(documentBytes) {
        val prepared =
            withContext(Dispatchers.IO) {
                player.prepareDocument(RemoteDocument(documentBytes))
            }
        if (prepared != null) {
            player.setPreparedDocument(prepared)
            // documentBytes changing re-runs this effect, so clear before re-adding to
            // avoid stacking duplicate listeners on the same document.
            player.document.document.clearActionCallbacks()
            player.document.document.addIdActionListener { id, metadata -> onAction(id, metadata) }
        }
    }

    AndroidView(factory = { player })
}
```

- [ ] **Step 2: Refactor `MainActivity.kt` to use the shared screen**

Replace `app/src/main/java/com/example/remotecomposehelloclient/MainActivity.kt`'s full contents with:

```kotlin
package com.example.remotecomposehelloclient

import android.content.ActivityNotFoundException
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.example.remotecomposehelloclient.theme.RemoteComposeHelloClientTheme

/**
 * Mirrors `CLICK_ME_ACTION_ID` in remote-compose-hello's RemoteComposeDocuments.kt.
 * Server and client agree on this id's meaning by convention -- there's no
 * shared code between the two repos, only a shared contract.
 */
private const val CLICK_ME_ACTION_ID = 1

/**
 * Mirrors `OPEN_LINK_ACTION_ID` in remote-compose-hello's RemoteComposeDocuments.kt.
 * The action's string metadata is the URL to open.
 */
private const val OPEN_LINK_ACTION_ID = 2

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()
        setContent {
            RemoteComposeHelloClientTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    HelloDocumentScreen()
                }
            }
        }
    }
}

@Composable
private fun HelloDocumentScreen() {
    val context = LocalContext.current
    RemoteDocumentScreen(
        fetch = ::fetchHelloDocument,
        onAction = { id, metadata ->
            when (id) {
                CLICK_ME_ACTION_ID -> {
                    Toast.makeText(context, "Received action id: $id", Toast.LENGTH_SHORT).show()
                }
                OPEN_LINK_ACTION_ID -> {
                    metadata?.let { openUrlInBrowser(context, it) }
                }
                else -> {
                    Toast.makeText(context, "Received action id: $id", Toast.LENGTH_SHORT).show()
                }
            }
        },
    )
}

private fun openUrlInBrowser(context: Context, url: String) {
    val normalizedUrl =
        if (url.startsWith("http://") || url.startsWith("https://")) {
            url
        } else {
            "https://$url"
        }
    try {
        CustomTabsIntent.Builder().build().launchUrl(context, Uri.parse(normalizedUrl))
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(context, "No browser available to open link", Toast.LENGTH_SHORT).show()
    }
}
```

- [ ] **Step 3: Compile to verify**

Run: `./gradlew :app:compileDebugKotlin`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/example/remotecomposehelloclient/RemoteDocumentScreen.kt \
        app/src/main/java/com/example/remotecomposehelloclient/MainActivity.kt
git commit -m "Extract RemoteDocumentScreen: shared fetch/render composable"
```

---

### Task 2: Add the News feature (`NewsDocumentClient`, `NewsActivity`, manifest entry, "View News" button)

**Files:**
- Create: `app/src/main/java/com/example/remotecomposehelloclient/NewsDocumentClient.kt`
- Create: `app/src/main/java/com/example/remotecomposehelloclient/NewsActivity.kt`
- Modify: `app/src/main/AndroidManifest.xml`
- Modify: `app/src/main/java/com/example/remotecomposehelloclient/MainActivity.kt`

**Interfaces:**
- Consumes: `RemoteDocumentScreen(fetch, onAction)` from Task 1.
- Produces: `suspend fun fetchNewsDocument(): ByteArray` — consumed by `NewsActivity`.
- Produces: `class NewsActivity : ComponentActivity()` — consumed by `MainActivity`'s button (via `Intent(context, NewsActivity::class.java)`).

- [ ] **Step 1: Create the news document client**

`app/src/main/java/com/example/remotecomposehelloclient/NewsDocumentClient.kt`:

```kotlin
package com.example.remotecomposehelloclient

import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * 10.0.2.2 is the Android emulator's alias for the host machine's localhost.
 * This assumes the remote-compose-hello Spring Boot server is running
 * locally (`./gradlew bootRun`) and this app is running in an emulator,
 * not a physical device.
 */
private const val NEWS_ENDPOINT = "http://10.0.2.2:8080/api/news"

private val client = OkHttpClient()

/** Fetches the RemoteCompose news carousel document bytes from the server. */
suspend fun fetchNewsDocument(): ByteArray =
    withContext(Dispatchers.IO) {
        val request = Request.Builder().url(NEWS_ENDPOINT).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("Unexpected response ${response.code} from $NEWS_ENDPOINT")
            }
            response.body?.bytes() ?: throw IOException("Empty response body from $NEWS_ENDPOINT")
        }
    }
```

- [ ] **Step 2: Create `NewsActivity`**

`app/src/main/java/com/example/remotecomposehelloclient/NewsActivity.kt`:

```kotlin
package com.example.remotecomposehelloclient

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.example.remotecomposehelloclient.theme.RemoteComposeHelloClientTheme

class NewsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()
        setContent {
            RemoteComposeHelloClientTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    RemoteDocumentScreen(fetch = ::fetchNewsDocument)
                }
            }
        }
    }
}
```

- [ ] **Step 3: Register `NewsActivity` in the manifest**

In `app/src/main/AndroidManifest.xml`, add this `<activity>` element directly after the existing `MainActivity` `</activity>` closing tag, still inside `<application>`:

```xml
        <activity
            android:name=".NewsActivity"
            android:exported="false" />
```

- [ ] **Step 4: Add the "View News" button to `MainActivity`**

Replace `app/src/main/java/com/example/remotecomposehelloclient/MainActivity.kt`'s full contents with:

```kotlin
package com.example.remotecomposehelloclient

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.remotecomposehelloclient.theme.RemoteComposeHelloClientTheme

/**
 * Mirrors `CLICK_ME_ACTION_ID` in remote-compose-hello's RemoteComposeDocuments.kt.
 * Server and client agree on this id's meaning by convention -- there's no
 * shared code between the two repos, only a shared contract.
 */
private const val CLICK_ME_ACTION_ID = 1

/**
 * Mirrors `OPEN_LINK_ACTION_ID` in remote-compose-hello's RemoteComposeDocuments.kt.
 * The action's string metadata is the URL to open.
 */
private const val OPEN_LINK_ACTION_ID = 2

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()
        setContent {
            RemoteComposeHelloClientTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    MainScreen()
                }
            }
        }
    }
}

@Composable
private fun MainScreen() {
    val context = LocalContext.current
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Button(
            onClick = { context.startActivity(Intent(context, NewsActivity::class.java)) },
            modifier = Modifier.padding(16.dp),
        ) {
            Text("View News")
        }
        Box(modifier = Modifier.weight(1f)) {
            HelloDocumentScreen()
        }
    }
}

@Composable
private fun HelloDocumentScreen() {
    val context = LocalContext.current
    RemoteDocumentScreen(
        fetch = ::fetchHelloDocument,
        onAction = { id, metadata ->
            when (id) {
                CLICK_ME_ACTION_ID -> {
                    Toast.makeText(context, "Received action id: $id", Toast.LENGTH_SHORT).show()
                }
                OPEN_LINK_ACTION_ID -> {
                    metadata?.let { openUrlInBrowser(context, it) }
                }
                else -> {
                    Toast.makeText(context, "Received action id: $id", Toast.LENGTH_SHORT).show()
                }
            }
        },
    )
}

private fun openUrlInBrowser(context: Context, url: String) {
    val normalizedUrl =
        if (url.startsWith("http://") || url.startsWith("https://")) {
            url
        } else {
            "https://$url"
        }
    try {
        CustomTabsIntent.Builder().build().launchUrl(context, Uri.parse(normalizedUrl))
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(context, "No browser available to open link", Toast.LENGTH_SHORT).show()
    }
}
```

Note (found during implementation): `Modifier.weight(1f)` above needs **no import at all** — `weight` is a member function of the `ColumnScope` interface (`fun Modifier.weight(weight: Float, fill: Boolean = true): Modifier`, declared directly on the interface in `androidx.compose.foundation.layout.Column.kt`), not a top-level extension, so it resolves automatically inside `Column { ... }`'s content lambda. There is a same-named but unrelated `internal val RowColumnParentData?.weight: Float` top-level property elsewhere in the same package; explicitly writing `import androidx.compose.foundation.layout.weight` binds to *that* internal property instead and fails to compile ("it is internal in file"). Leave `weight` out of the import list entirely.

- [ ] **Step 5: Compile to verify**

Run: `./gradlew :app:compileDebugKotlin`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/example/remotecomposehelloclient/NewsDocumentClient.kt \
        app/src/main/java/com/example/remotecomposehelloclient/NewsActivity.kt \
        app/src/main/AndroidManifest.xml \
        app/src/main/java/com/example/remotecomposehelloclient/MainActivity.kt
git commit -m "Add News screen: NewsActivity, NewsDocumentClient, View News button"
```

---

### Task 3: Build, install, and verify end-to-end; document the result

**Files:**
- Modify: `docs/design.md`

**Interfaces:** None — verification and documentation only.

- [ ] **Step 1: Start the emulator (not the attached physical device)**

```bash
~/Library/Android/sdk/emulator/emulator -avd Medium_Phone_API_36.1 &
adb wait-for-device
```

Poll until boot completes (repeat until it prints `1`):

```bash
adb -s emulator-5554 shell getprop sys.boot_completed
```

(If the emulator enumerates under a different serial, use `adb devices` to find it and substitute it for `emulator-5554` in every step below.)

- [ ] **Step 2: Start the backend**

In `/Users/gopal/Projects/Spring/remote-compose-hello`:

```bash
NEWSAPI_API_KEY="$NEWSAPI_API_KEY" ./gradlew bootRun
```

(Run in the background / a separate terminal so it keeps serving while you test the app. If `NEWSAPI_API_KEY` isn't set to a real key, `/api/news` will return `502` — that's fine, it exercises the Error/Retry path; note in Step 5 which case this run actually covers.)

- [ ] **Step 3: Build and install on the emulator**

In this repo (`RemoteComposeHelloClient`):

```bash
ANDROID_SERIAL=emulator-5554 ./gradlew installDebug
```

- [ ] **Step 4: Launch and drive the app**

Use the `run` skill (or, without it, `adb -s emulator-5554 shell am start -n com.example.remotecomposehelloclient/.MainActivity` followed by `adb -s emulator-5554 exec-out screencap -p > screenshot.png` and reading the screenshot) to:

1. Confirm the Hello screen still renders unchanged: "Hello world" text, purple "Click me" button, blue "Open Link" button, and the new "View News" button, all visible with no crash.
2. Tap "Click me" and confirm the existing Toast still fires (regression check for Task 1's refactor).
3. Tap "View News" and confirm `NewsActivity` opens.
4. On the News screen, confirm either:
   - real article cards render (image, title, description) in a horizontally scrollable row, if `NEWSAPI_API_KEY` was a real key -- scroll to confirm horizontal scrolling works, or
   - the Error/Retry UI renders (if no real key was set), and tapping Retry re-attempts the fetch without crashing.
5. Check for crashes via logcat: `adb -s emulator-5554 logcat -d | grep -iE "FATAL|NetworkOnMainThread"` should show nothing related to this app.

- [ ] **Step 5: Update `docs/design.md`**

Add a new section at the end of `docs/design.md`, following the existing "Verified end-to-end" section's style, titled `## News screen` (or similar), documenting:
- What `NewsActivity`/`fetchNewsDocument`/the "View News" button do (a short architecture note, mirroring the existing top-of-doc style).
- The actual result of Step 4's manual verification -- write down what was *actually observed* (which case applied: real cards with images, or Error/Retry due to no key), not a pre-supposed outcome. If only the Error/Retry path could be verified due to no `NEWSAPI_API_KEY` being available, say so explicitly and note that image-rendering verification (confirming `prepareDocument`/`setPreparedDocument` resolves real URLs correctly) remains an open follow-up once a real key is available.

- [ ] **Step 6: Commit**

```bash
git add docs/design.md
git commit -m "Document News screen and its end-to-end verification"
```

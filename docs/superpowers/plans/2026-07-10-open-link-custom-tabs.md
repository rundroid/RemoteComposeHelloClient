# Open Link Action → Custom Tabs Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make tapping the "Open Link" button in the rendered RemoteCompose document open `www.wellsfargo.com` in a Chrome Custom Tab, while the existing "Click me" button (and any other/unknown action id) keeps its current demo-Toast behavior.

**Architecture:** `RemoteComposeDocumentView` in `MainActivity.kt` already registers `player.document.document.addIdActionListener { id, metadata -> ... }`; today it ignores `metadata` and always shows the same Toast. This plan adds a `when (id)` branch that, for `OPEN_LINK_ACTION_ID`, passes `metadata` (the URL string sent by the server) to a new `openUrlInBrowser` helper built on `androidx.browser`'s `CustomTabsIntent`.

**Tech Stack:** Kotlin, Jetpack Compose, `androidx.compose.remote:remote-player-view:1.0.0-alpha14`, `androidx.browser:browser:1.10.0` (new dependency).

## Global Constraints

- Action ids mirror the server's `remote-compose-hello` constants by convention, not shared code: `CLICK_ME_ACTION_ID = 1`, `OPEN_LINK_ACTION_ID = 2`.
- The server sends the URL with no scheme (`"www.wellsfargo.com"`); the client must prepend `https://` when the metadata string has no `http://`/`https://` prefix already, since `Uri.parse` on a bare host has no authority and Custom Tabs cannot launch it.
- Unknown/unrecognized action ids must keep behaving exactly as they do today (generic Toast) — no regression for `CLICK_ME_ACTION_ID` or any future id this plan doesn't know about.
- End-to-end verification uses the `Medium_Phone_API_36.1` emulator against a locally running `remote-compose-hello` instance, matching this project's existing documented verification method (`docs/design.md`'s "Verified end-to-end" section) — not the physical device that may also be attached via adb.

---

### Task 1: Wire `OPEN_LINK_ACTION_ID` to Custom Tabs and confirm it compiles

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `app/build.gradle.kts`
- Modify: `app/src/main/java/com/example/remotecomposehelloclient/MainActivity.kt`

**Interfaces:**
- Consumes: `RemoteComposePlayer` (existing, from `remote-player-view`), `player.document.document.addIdActionListener { id: Int, metadata: String? -> ... }` (existing signature, already used in this file).
- Produces: `private const val CLICK_ME_ACTION_ID = 1`, `private const val OPEN_LINK_ACTION_ID = 2`, `private fun openUrlInBrowser(context: Context, url: String)` — all top-level/private in `MainActivity.kt`, not consumed outside this file.

- [ ] **Step 1: Add the `androidx.browser` version and library alias**

  Open `gradle/libs.versions.toml`. In the `[versions]` block, after the `androidxComposeBom` line, add:

  ```toml
  androidxBrowser = "1.10.0"
  ```

  In the `[libraries]` block, after the `androidx-compose-ui-test-manifest` line, add:

  ```toml
  androidx-browser = { module = "androidx.browser:browser", version.ref = "androidxBrowser" }
  ```

- [ ] **Step 2: Add the dependency to the app module**

  Open `app/build.gradle.kts`. In the `dependencies { ... }` block, after the `implementation(libs.androidx.compose.material3)` line, add:

  ```kotlin
  implementation(libs.androidx.browser)
  ```

- [ ] **Step 3: Verify the dependency resolves**

  Run: `./gradlew :app:dependencies --configuration debugRuntimeClasspath | grep browser`

  Expected: output includes a line like `androidx.browser:browser:1.10.0`.

- [ ] **Step 4: Add the action id constants**

  Open `app/src/main/java/com/example/remotecomposehelloclient/MainActivity.kt`. After the imports (after the `com.example.remotecomposehelloclient.theme.RemoteComposeHelloClientTheme` import, before the `private sealed interface DocumentState` declaration), add:

  ```kotlin

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
  ```

- [ ] **Step 5: Add the required imports**

  In the same file's import block, the four new imports go in four different
  alphabetical spots. Replace:

  ```kotlin
  import android.os.Bundle
  import android.widget.Toast
  import androidx.activity.ComponentActivity
  import androidx.activity.compose.setContent
  import androidx.activity.enableEdgeToEdge
  import androidx.compose.foundation.layout.Arrangement
  ```

  with:

  ```kotlin
  import android.content.ActivityNotFoundException
  import android.content.Context
  import android.net.Uri
  import android.os.Bundle
  import android.widget.Toast
  import androidx.activity.ComponentActivity
  import androidx.activity.compose.setContent
  import androidx.activity.enableEdgeToEdge
  import androidx.browser.customtabs.CustomTabsIntent
  import androidx.compose.foundation.layout.Arrangement
  ```

- [ ] **Step 6: Replace the unconditional Toast with a `when (id)` block**

  In `RemoteComposeDocumentView`, replace:

  ```kotlin
            player.document.document.addIdActionListener { id, _ ->
                Toast.makeText(context, "Received action id: $id", Toast.LENGTH_SHORT).show()
            }
  ```

  with:

  ```kotlin
            player.document.document.addIdActionListener { id, metadata ->
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
            }
  ```

- [ ] **Step 7: Add the `openUrlInBrowser` helper**

  In the same file, after the closing brace of `RemoteComposeDocumentView`, add:

  ```kotlin

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

- [ ] **Step 8: Compile the app**

  Run: `./gradlew :app:compileDebugKotlin`

  Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 9: Commit**

  ```bash
  git add gradle/libs.versions.toml app/build.gradle.kts app/src/main/java/com/example/remotecomposehelloclient/MainActivity.kt
  git commit -m "Open www.wellsfargo.com via Custom Tabs for OPEN_LINK_ACTION_ID"
  ```

  Note: this repo also has a pre-existing unrelated uncommitted change (a generated `equals`/`hashCode` override on `DocumentState.Ready`) — do not revert or otherwise touch it; only stage the files listed above.

---

### Task 2: Verify end-to-end on the emulator and update the docs

**Files:**
- Modify: `docs/design.md`

**Interfaces:**
- Consumes: the built app from Task 1 (`app/build.gradle.kts`'s `debug` build type) and a running `remote-compose-hello` server (from the sibling repo, `/Users/gopal/Projects/Spring/remote-compose-hello`).
- Produces: nothing consumed by later tasks (this is the final task).

- [ ] **Step 1: Start the server**

  In a background shell, from `/Users/gopal/Projects/Spring/remote-compose-hello`:

  ```bash
  ./gradlew bootRun
  ```

  Expected (after ~10-30s): log line containing `Started RemoteComposeHelloApplication`.

- [ ] **Step 2: Start the `Medium_Phone_API_36.1` emulator**

  ```bash
  ~/Library/Android/sdk/emulator/emulator -avd Medium_Phone_API_36.1 -no-snapshot-load &
  ```

  Then wait for boot:

  ```bash
  ~/Library/Android/sdk/platform-tools/adb wait-for-device
  ~/Library/Android/sdk/platform-tools/adb shell 'while [[ -z $(getprop sys.boot_completed) ]]; do sleep 1; done'
  ```

  Expected: both commands return without error once the emulator has booted.

- [ ] **Step 3: Identify the emulator's adb serial**

  Run: `~/Library/Android/sdk/platform-tools/adb devices`

  Expected: a line like `emulator-5554	device` alongside any physical device also listed (e.g. `10BG1D1EGA009YS	device`). Use the `emulator-XXXX` serial for every subsequent `adb`/Gradle install command (e.g. via `-s emulator-5554`, or `ANDROID_SERIAL=emulator-5554`), not the physical device.

- [ ] **Step 4: Build and install the app on the emulator**

  From `/Users/gopal/Projects/Sample_Android_Projects/RemoteComposeHelloClient`:

  ```bash
  ANDROID_SERIAL=emulator-5554 ./gradlew installDebug
  ```

  (substitute the actual serial from Step 3 if different)

  Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Launch the app**

  ```bash
  adb -s emulator-5554 shell am start -n com.example.remotecomposehelloclient/.MainActivity
  ```

  Expected: `Starting: Intent { ... }` with no error.

- [ ] **Step 6: Screenshot to confirm the document rendered and locate the "Open Link" button**

  ```bash
  adb -s emulator-5554 exec-out screencap -p > /tmp/step6-rendered.png
  ```

  Read `/tmp/step6-rendered.png`. Expected: "Hello world" text, a purple "Click me" button, and a blue "Open Link" button rendered below it, per `remote-compose-hello`'s document layout.

- [ ] **Step 7: Tap "Open Link" and confirm Custom Tabs opens the URL**

  Using the pixel coordinates of the "Open Link" button observed in the Step 6 screenshot:

  ```bash
  adb -s emulator-5554 shell input tap <x> <y>
  sleep 1
  adb -s emulator-5554 exec-out screencap -p > /tmp/step7-after-tap.png
  ```

  Read `/tmp/step7-after-tap.png`. Expected: a Chrome Custom Tab is now in the foreground showing `wellsfargo.com` (or its redirect target) loading/loaded — not the app's own UI, and not a crash dialog.

- [ ] **Step 8: Tap "Click me" and confirm the existing Toast still works (no regression)**

  ```bash
  adb -s emulator-5554 shell am start -n com.example.remotecomposehelloclient/.MainActivity
  sleep 1
  adb -s emulator-5554 shell input tap <x> <y>
  sleep 1
  adb -s emulator-5554 exec-out screencap -p > /tmp/step8-click-me.png
  ```

  (using the "Click me" button's coordinates from the Step 6 screenshot)

  Read `/tmp/step8-click-me.png`. Expected: a Toast reading "Received action id: 1" is visible, confirming the `CLICK_ME_ACTION_ID` branch is unaffected.

- [ ] **Step 9: Update `docs/design.md`**

  Open `docs/design.md`. In the "## Verified end-to-end" section, after the existing numbered list (which ends with the `CLICK_ME_ACTION_ID` Toast confirmation), add:

  ```markdown
  3. Tapping "Open Link" opened `www.wellsfargo.com` in a Chrome Custom Tab —
     confirming `OPEN_LINK_ACTION_ID`'s metadata (the URL) round-trips through the
     document and `openUrlInBrowser` correctly.
  ```

  In the "## Known limitations" section, replace:

  ```markdown
  - No app-side handling for `CLICK_ME_ACTION_ID` beyond the demonstration
    Toast — a real app would look up the id in a `when` block mapping ids to
    behavior (network calls, navigation, etc.), as described in
    `remote-compose-hello`'s own `docs/design.md`.
  ```

  with:

  ```markdown
  - `CLICK_ME_ACTION_ID` still only shows a demonstration Toast — a real app
    would replace it with actual behavior (network calls, navigation, etc.).
    `OPEN_LINK_ACTION_ID` is the first action id with real behavior: it opens
    its metadata URL in a Custom Tab (see `openUrlInBrowser` in
    `MainActivity.kt`).
  - No URL validation beyond scheme-normalization (`https://` is prepended
    when missing) — a malformed or unexpected metadata string would be passed
    to Custom Tabs as-is.
  ```

- [ ] **Step 10: Stop the emulator and server**

  ```bash
  adb -s emulator-5554 emu kill
  ```

  Stop the `./gradlew bootRun` background process from Step 1 (e.g. `kill` its process, or Ctrl-C if run in foreground).

- [ ] **Step 11: Commit**

  ```bash
  git add docs/design.md
  git commit -m "Document verified Open Link -> Custom Tabs behavior"
  ```

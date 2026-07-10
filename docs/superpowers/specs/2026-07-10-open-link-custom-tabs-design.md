# Open Link action → Custom Tabs design

## Purpose

The `remote-compose-hello` server now serves a second button, "Open Link", whose
`HostAction` carries action id `OPEN_LINK_ACTION_ID = 2` plus the URL
`"www.wellsfargo.com"` as string metadata (see that repo's
`docs/design.md`). This client currently ignores the action's metadata and shows
the same generic "Received action id: N" Toast for every id. This change makes
tapping "Open Link" actually open the URL, using Chrome Custom Tabs, while
preserving the existing demo Toast behavior for `CLICK_ME_ACTION_ID` (and any
other/unknown id).

## Approach

`RemoteComposePlayer`'s `addIdActionListener(id, metadata)` callback (already wired
up in `RemoteComposeDocumentView` in `MainActivity.kt`) already receives both the
action id and its optional string metadata — the metadata parameter is currently
discarded (`{ id, _ -> ... }`). No new player-side API is needed; this is purely
about branching on `id` and consuming `metadata`.

Custom Tabs (`androidx.browser:browser`) is the standard way to open a URL in-app
without leaving to a separate browser Activity or building a WebView. The library
is not yet a dependency of this project but version `1.10.0` is already present in
the local Gradle cache, so adding it needs no network access to resolve.

The server sends a bare host, `"www.wellsfargo.com"`, with no `http(s)://` scheme.
`Uri.parse` on a schemeless string produces a `Uri` with no authority that Custom
Tabs cannot launch as a browsable intent, so the client must prepend `https://`
before parsing when no scheme is already present.

## Changes

**`gradle/libs.versions.toml` / `app/build.gradle.kts`**
- Add version `androidxBrowser = "1.10.0"` and library alias
  `androidx-browser = { module = "androidx.browser:browser", version.ref =
  "androidxBrowser" }`.
- Add `implementation(libs.androidx.browser)` to `app/build.gradle.kts`.

**`MainActivity.kt`**
- Add two top-level constants, documented as mirroring the identically-named
  constants in `remote-compose-hello`'s `RemoteComposeDocuments.kt` (a
  server/client naming contract, not shared code across the two repos):
  ```kotlin
  private const val CLICK_ME_ACTION_ID = 1
  private const val OPEN_LINK_ACTION_ID = 2
  ```
- In `RemoteComposeDocumentView`, change the listener from an unconditional Toast
  to a `when (id)` block:
  - `CLICK_ME_ACTION_ID` → existing Toast, unchanged behavior.
  - `OPEN_LINK_ACTION_ID` → call `metadata?.let { openUrlInBrowser(context, it) }`.
  - `else` → same generic Toast as today, so unrecognized ids keep behaving exactly
    as they do now (no regression).
- Add a private function:
  ```kotlin
  private fun openUrlInBrowser(context: Context, url: String) {
      val normalizedUrl = if (url.startsWith("http://") || url.startsWith("https://")) {
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

**`docs/design.md`**
- Update "Known limitations" to reflect that `OPEN_LINK_ACTION_ID` now has real
  behavior (opens the URL via Custom Tabs), rather than stating there's no
  app-side handling beyond a Toast for any id.

## Out of scope

- Any change to the server (`remote-compose-hello`) — already shipped.
- Generalizing to arbitrary/unknown link-carrying action ids — this wires up the
  one concrete `OPEN_LINK_ACTION_ID` the server currently sends, matching the
  existing single-purpose handling of `CLICK_ME_ACTION_ID`.
- In-app custom UI chrome for the Custom Tabs (toolbar color, etc.) — default
  `CustomTabsIntent.Builder()` styling is sufficient for this sample.

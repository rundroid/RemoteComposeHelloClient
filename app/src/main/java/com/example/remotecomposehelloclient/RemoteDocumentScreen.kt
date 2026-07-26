package com.example.remotecomposehelloclient

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.remote.core.Limits
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
        // Documents referencing images by URL (ENCODING_URL bitmaps) are rejected during
        // parsing unless this is explicitly enabled -- it's a deliberate safety gate against
        // a document making the app fetch arbitrary attacker-supplied URLs. Accepted here
        // because this app only ever loads documents from remote-compose-hello, a server we
        // control.
        Limits.ENABLE_IMAGE_URLS = true
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

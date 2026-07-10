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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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

private sealed interface DocumentState {
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
    var state by remember { mutableStateOf<DocumentState>(DocumentState.Loading) }
    var loadAttempt by remember { mutableIntStateOf(0) }

    LaunchedEffect(loadAttempt) {
        state = DocumentState.Loading
        state =
            try {
                DocumentState.Ready(fetchHelloDocument())
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
            is DocumentState.Ready -> RemoteComposeDocumentView(current.bytes)
        }
    }
}

@Composable
private fun RemoteComposeDocumentView(documentBytes: ByteArray) {
    val context = LocalContext.current
    AndroidView(
        factory = { RemoteComposePlayer(it) },
        update = { player ->
            player.setDocument(documentBytes)
            // Recomposition re-runs this block, so clear before re-adding to
            // avoid stacking duplicate listeners on the same document.
            player.document.document.clearActionCallbacks()
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

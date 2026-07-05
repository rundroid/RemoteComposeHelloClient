package com.example.remotecomposehelloclient

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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

private sealed interface DocumentState {
    data object Loading : DocumentState

    data class Error(val message: String) : DocumentState

    data class Ready(val bytes: ByteArray) : DocumentState
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
            player.document.document.addIdActionListener { id, _ ->
                Toast.makeText(context, "Received action id: $id", Toast.LENGTH_SHORT).show()
            }
        },
    )
}

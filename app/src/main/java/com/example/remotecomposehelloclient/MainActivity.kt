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

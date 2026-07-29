package com.example.remotecomposehelloclient

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.remotecomposehelloclient.theme.RemoteComposeHelloClientTheme

/**
 * Mirrors `OPEN_ARTICLE_ACTION_ID` in remote-compose-hello's NewsCarouselDocument.kt.
 * The action's string metadata is the tapped card's article URL.
 */
private const val OPEN_ARTICLE_ACTION_ID = 1

class NewsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()
        setContent {
            RemoteComposeHelloClientTheme {
                Surface(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = 48.dp),
                    color = MaterialTheme.colorScheme.background
                ) {
                    NewsScreen()
                }
            }
        }
    }
}

@Composable
private fun NewsScreen() {
    val context = LocalContext.current
    RemoteDocumentScreen(
        fetch = ::fetchNewsDocument,
        onAction = { id, metadata ->
            if (id == OPEN_ARTICLE_ACTION_ID) {
                metadata?.let { openUrlInBrowser(context, it) }
            }
        },
    )
}

package com.example.remotecomposehelloclient

import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

private const val NEWS_ENDPOINT =
    "https://remote-compose-hello-675147774817.us-central1.run.app/api/news"

private val client = OkHttpClient()

/** Fetches the RemoteCompose news carousel document bytes from the server. */
suspend fun fetchNewsDocument(): ByteArray =
    withContext(Dispatchers.IO) {
        val request = Request.Builder().url(NEWS_ENDPOINT).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("Unexpected response ${response.code} from $NEWS_ENDPOINT")
            }
            response.body.bytes()
        }
    }

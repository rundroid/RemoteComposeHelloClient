package com.example.remotecomposehelloclient

import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

private const val HELLO_ENDPOINT =
    "https://remote-compose-hello-675147774817.us-central1.run.app/api/hello"

private val client = OkHttpClient()

/** Fetches the RemoteCompose "Hello world" document bytes from the server. */
suspend fun fetchHelloDocument(): ByteArray =
    withContext(Dispatchers.IO) {
        val request = Request.Builder().url(HELLO_ENDPOINT).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("Unexpected response ${response.code} from $HELLO_ENDPOINT")
            }
            response.body.bytes()
        }
    }

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
private const val HELLO_ENDPOINT = "http://10.0.2.2:8080/api/hello"

private val client = OkHttpClient()

/** Fetches the RemoteCompose "Hello world" document bytes from the server. */
suspend fun fetchHelloDocument(): ByteArray =
    withContext(Dispatchers.IO) {
        val request = Request.Builder().url(HELLO_ENDPOINT).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("Unexpected response ${response.code} from $HELLO_ENDPOINT")
            }
            response.body?.bytes() ?: throw IOException("Empty response body from $HELLO_ENDPOINT")
        }
    }

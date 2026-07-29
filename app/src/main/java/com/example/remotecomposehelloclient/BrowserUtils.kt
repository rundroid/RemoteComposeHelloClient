package com.example.remotecomposehelloclient

import android.content.ActivityNotFoundException
import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.browser.customtabs.CustomTabsIntent

/**
 * Opens [url] in a Chrome Custom Tab, prepending `https://` if the URL has no scheme
 * (RemoteCompose documents may carry a bare host, e.g. `www.wellsfargo.com`, or a full
 * article URL as action metadata). Shows a Toast instead of crashing if no browser is
 * available.
 */
fun openUrlInBrowser(context: Context, url: String) {
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

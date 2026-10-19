package es.sergiotech.tickettracker.data

import android.content.Intent
import android.net.Uri
import android.os.Build

data class SharedReceipt(val uris: List<Uri> = emptyList(), val text: String? = null)

@Suppress("DEPRECATION")
fun sharedReceipt(intent: Intent?): SharedReceipt {
    if (
        intent?.action !in
            listOf(Intent.ACTION_SEND, Intent.ACTION_SEND_MULTIPLE, Intent.ACTION_VIEW)
    )
        return SharedReceipt()
    val uris = mutableListOf<Uri>()
    if (intent?.action == Intent.ACTION_VIEW) intent.data?.let(uris::add)
    else if (intent?.action == Intent.ACTION_SEND_MULTIPLE) {
        val list =
            if (Build.VERSION.SDK_INT >= 33)
                intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)
            else intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)
        list?.let(uris::addAll)
    } else {
        val uri =
            if (Build.VERSION.SDK_INT >= 33)
                intent?.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
            else intent?.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
        uri?.let(uris::add)
    }
    intent?.clipData?.let { clip ->
        for (index in 0 until minOf(clip.itemCount, 20)) clip.getItemAt(index).uri?.let(uris::add)
    }
    val content = uris.filter { it.scheme == "content" }.distinct().take(20)
    val text =
        if (content.isEmpty() && intent?.type == "text/plain")
            intent
                .getCharSequenceExtra(Intent.EXTRA_TEXT)
                ?.toString()
                ?.take(100_000)
                ?.takeIf(String::isNotBlank)
        else null
    return SharedReceipt(content, text)
}

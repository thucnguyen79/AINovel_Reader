package vn.esstar.ainovel.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.network.NetworkHeaders
import coil3.network.httpHeaders
import coil3.request.ImageRequest
import coil3.request.crossfade
import java.net.URI

/**
 * Cover image for a book. Story-site CDNs usually reject requests without a Referer from their own
 * domain, so the referer is derived from [refererUrl] (normally the book's page URL).
 */
@Composable
fun BookCover(
    url: String?,
    refererUrl: String?,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 8.dp,
    contentDescription: String? = null,
) {
    val context = LocalContext.current
    val shape = RoundedCornerShape(cornerRadius)
    Box(
        modifier = modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
    ) {
        if (!url.isNullOrBlank()) {
            val request = ImageRequest.Builder(context)
                .data(url)
                .crossfade(true)
                .apply {
                    originOf(refererUrl)?.let { origin ->
                        httpHeaders(NetworkHeaders.Builder().set("Referer", origin).build())
                    }
                }
                .build()
            AsyncImage(
                model = request,
                contentDescription = contentDescription,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize(),
            )
        }
    }
}

/** `https://www.tvtruyen.space/abc.html` -> `https://www.tvtruyen.space/`. */
fun originOf(url: String?): String? {
    if (url.isNullOrBlank()) return null
    return runCatching {
        val u = URI(url)
        if (u.scheme == null || u.host == null) null else "${u.scheme}://${u.host}/"
    }.getOrNull()
}

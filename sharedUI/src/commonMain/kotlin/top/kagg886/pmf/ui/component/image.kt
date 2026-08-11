package top.kagg886.pmf.ui.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.DefaultAlpha
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.drawscope.DrawScope.Companion.DefaultFilterQuality
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImagePainter
import coil3.compose.AsyncImagePainter.Companion.DefaultTransform
import coil3.compose.LocalPlatformContext
import coil3.compose.rememberAsyncImagePainter
import coil3.compose.rememberConstraintsSizeResolver
import coil3.request.ImageRequest
import com.eygraber.compose.placeholder.fade
import com.eygraber.compose.placeholder.material3.fade
import com.eygraber.compose.placeholder.material3.shimmer
import top.kagg886.pmf.ui.util.applyIf

@Composable
fun ProgressableImage(
    model: Any?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    transform: (AsyncImagePainter.State) -> AsyncImagePainter.State = DefaultTransform,
    onState: ((AsyncImagePainter.State) -> Unit)? = null,
    alignment: Alignment = Alignment.Center,
    contentScale: ContentScale = ContentScale.Fit,
    alpha: Float = DefaultAlpha,
    colorFilter: ColorFilter? = null,
    filterQuality: FilterQuality = DefaultFilterQuality,
    clipToBounds: Boolean = true,
) {
    val sizeResolver = rememberConstraintsSizeResolver()

    val painter = rememberAsyncImagePainter(
        model = ImageRequest.Builder(LocalPlatformContext.current)
            .data(model)
            .size(sizeResolver)
            .build(),
        transform = transform,
        onState = onState,
        contentScale = contentScale,
        filterQuality = filterQuality,
    )

    val state by painter.state.collectAsState()

    Box(modifier = modifier) {
        Image(
            painter = painter,
            contentDescription = contentDescription,
            modifier = Modifier
                .matchParentSize()
                .then(sizeResolver)
                .applyIf(clipToBounds, Modifier.clipToBounds()),
            alignment = alignment,
            contentScale = contentScale,
            alpha = alpha,
            colorFilter = colorFilter,
        )

        when (state) {
            AsyncImagePainter.State.Empty,
            is AsyncImagePainter.State.Loading -> {
                Box(
                    modifier = Modifier.matchParentSize()
                )
            }

            is AsyncImagePainter.State.Error -> {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "重新加载",
                    modifier = Modifier
                        .align(Alignment.Center)
                        .clickable {
                            painter.restart()
                        },
                )
            }

            is AsyncImagePainter.State.Success -> Unit
        }
    }
}

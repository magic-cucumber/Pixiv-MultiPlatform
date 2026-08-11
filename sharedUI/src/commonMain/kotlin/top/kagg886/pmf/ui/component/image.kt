package top.kagg886.pmf.ui.component

import androidx.compose.animation.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.DefaultAlpha
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope.Companion.DefaultFilterQuality
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImagePainter
import coil3.compose.AsyncImagePainter.Companion.DefaultTransform
import coil3.compose.LocalPlatformContext
import coil3.compose.rememberAsyncImagePainter
import coil3.compose.rememberConstraintsSizeResolver
import coil3.request.ImageRequest
import org.jetbrains.compose.resources.painterResource
import top.kagg886.pmf.res.Res
import top.kagg886.pmf.res.pixiv
import top.kagg886.pmf.ui.util.applyIf
import top.kagg886.pmf.ui.util.placeholder

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

    ProgressableImageContent(
        painter = painter,
        state = when (state) {
            AsyncImagePainter.State.Empty,
            is AsyncImagePainter.State.Loading -> ProgressableImageState.Loading

            is AsyncImagePainter.State.Success -> ProgressableImageState.Success
            is AsyncImagePainter.State.Error -> ProgressableImageState.Error
        },
        contentDescription = contentDescription,
        modifier = modifier,
        imageModifier = sizeResolver,
        onRetry = painter::restart,
        alignment = alignment,
        contentScale = contentScale,
        alpha = alpha,
        colorFilter = colorFilter,
        clipToBounds = clipToBounds,
    )
}

private enum class ProgressableImageState {
    Loading,
    Success,
    Error,
}

@Composable
private fun ProgressableImageContent(
    painter: Painter,
    state: ProgressableImageState,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    imageModifier: Modifier = Modifier,
    onRetry: () -> Unit = {},
    alignment: Alignment = Alignment.Center,
    contentScale: ContentScale = ContentScale.Fit,
    alpha: Float = DefaultAlpha,
    colorFilter: ColorFilter? = null,
    clipToBounds: Boolean = true,
) {
    AnimatedContent(
        targetState = state,
        modifier = modifier,
        transitionSpec = {
            if (initialState == ProgressableImageState.Loading && targetState == ProgressableImageState.Success) {
                (fadeIn() + expandIn(expandFrom = Alignment.Center)) togetherWith fadeOut()
            } else {
                fadeIn() togetherWith fadeOut()
            }
        },
        contentKey = { it },
    ) { animatedState ->
        Box(modifier = Modifier.fillMaxSize()) {
            Image(
                painter = painter,
                contentDescription = contentDescription,
                modifier = Modifier
                    .matchParentSize()
                    .placeholder(visible = animatedState == ProgressableImageState.Loading)
                    .then(imageModifier)
                    .applyIf(clipToBounds, Modifier.clipToBounds()),
                alignment = alignment,
                contentScale = contentScale,
                alpha = alpha,
                colorFilter = colorFilter,
            )

            if (animatedState == ProgressableImageState.Error) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .clickable(
                            interactionSource = null,
                            indication = null,
                            onClick = onRetry,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "refresh",
                    )
                }
            }
        }
    }
}

@Preview(name = "Loading", showBackground = true)
@Composable
private fun ProgressableImageLoadingPreview() {
    MaterialTheme {
        ProgressableImageContent(
            painter = ColorPainter(Color.Transparent),
            state = ProgressableImageState.Loading,
            contentDescription = null,
            modifier = Modifier.size(width = 200.dp, height = 120.dp),
        )
    }
}

@Preview(name = "Success", showBackground = true)
@Composable
private fun ProgressableImageSuccessPreview() {
    MaterialTheme {
        ProgressableImageContent(
            painter = painterResource(Res.drawable.pixiv),
            state = ProgressableImageState.Success,
            contentDescription = null,
            modifier = Modifier.size(width = 200.dp, height = 120.dp),
        )
    }
}

@Preview(name = "Error", showBackground = true)
@Composable
private fun ProgressableImageErrorPreview() {
    MaterialTheme {
        ProgressableImageContent(
            painter = ColorPainter(Color.Transparent),
            state = ProgressableImageState.Error,
            contentDescription = null,
            modifier = Modifier.size(width = 200.dp, height = 120.dp),
        )
    }
}

@Preview(
    name = "Interactive state transitions",
    widthDp = 1024,
    heightDp = 768,
    showBackground = true,
)
@Composable
fun ProgressableImageInteractivePreview() {
    var state by remember { mutableStateOf(ProgressableImageState.Loading) }

    val successPainter = painterResource(Res.drawable.pixiv)

    var painter by remember {
        mutableStateOf<Painter>(successPainter)
    }

    LaunchedEffect(state) {
        painter = if (state == ProgressableImageState.Success) { successPainter } else {
            object : Painter() {
                override val intrinsicSize: Size = Size(120f,120f)

                override fun DrawScope.onDraw() {
                }
            }
        }
    }

    MaterialTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(0.8f),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                ProgressableImageContent(
                    painter = painter,
                    state = state,
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(480.dp),
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Button(onClick = { state = ProgressableImageState.Loading }) {
                        Text("Switch to Loading")
                    }
                    Button(onClick = { state = ProgressableImageState.Success }) {
                        Text("Switch to Success")
                    }
                    Button(onClick = { state = ProgressableImageState.Error }) {
                        Text("Switch to Error")
                    }
                }
            }
        }
    }
}

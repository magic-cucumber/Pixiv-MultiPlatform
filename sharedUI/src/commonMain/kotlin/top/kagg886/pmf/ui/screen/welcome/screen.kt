package top.kagg886.pmf.ui.screen.welcome

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.serialization.Serializable
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.orbitmvi.orbit.compose.collectAsState
import org.orbitmvi.orbit.compose.collectSideEffect
import top.kagg886.pmf.BuildConfig
import top.kagg886.pmf.LocalNavController
import top.kagg886.pmf.i18n.Lang
import top.kagg886.pmf.i18n.welcome_start
import top.kagg886.pmf.i18n.welcome_tagline
import top.kagg886.pmf.ui.screen.login.LoginRoute
import top.kagg886.pmf.ui.screen.main.MainRoute
import top.kagg886.pmf.res.Res
import top.kagg886.pmf.res.pixiv
import top.kagg886.pmf.util.nav3.SerializableNavKey

/**
 * ================================================
 * Author:     iveou
 * Created on: 2026/7/27 13:41
 * ================================================
 */

@Serializable
data object WelcomeRoute: SerializableNavKey


@Composable
fun WelcomeScreen() {
    val model = viewModel<WelcomeViewModel> {
        WelcomeViewModel()
    }
    val nav = LocalNavController.current
    model.collectSideEffect {
        when(it) {
            WelcomeViewModelEffect.NavigateToLogin -> {
                nav.update {
                    pop()
                    push(LoginRoute)
                }
            }
            WelcomeViewModelEffect.NavigateToMain -> {
                nav.update {
                    pop()
                    push(MainRoute)
                }
            }
        }
    }

    val state by model.collectAsState()

    if (state.loading) return

    WelcomeScreenContent(
        onStart = model::confirmInitialized
    )
}

@Composable
private fun WelcomeScreenContent(
    onStart: () -> Unit
) {
    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.weight(1f))

            Surface(
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Image(
                    painter = painterResource(Res.drawable.pixiv),
                    contentDescription = null,
                    modifier = Modifier
                        .padding(24.dp)
                        .size(72.dp)
                )
            }

            Spacer(Modifier.height(24.dp))

            Text(
                text = BuildConfig.APP_NAME,
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(Modifier.height(8.dp))

            Text(
                text = stringResource(Lang.string.welcome_tagline),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.weight(1f))

            Button(
                onClick = onStart,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 32.dp)
            ) {
                Text(stringResource(Lang.string.welcome_start))
            }
        }
    }
}

@Preview
@Composable
private fun WelcomeScreenPreview() {
    MaterialTheme {
        WelcomeScreenContent(onStart = {})
    }
}

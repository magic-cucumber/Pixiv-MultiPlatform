package top.kagg886.pmf.ui.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import top.kagg886.pmf.LocalNavBackStack
import top.kagg886.pmf.ui.util.removeLastOrNullWorkaround

@Composable
fun ErrorPage(
    modifier: Modifier = Modifier,
    showBackButton: Boolean = false,
    text: String,
    onClick: () -> Unit,
) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text)
            Spacer(Modifier.height(16.dp))
            FloatingActionButton(onClick = onClick) {
                Icon(imageVector = Icons.Default.Refresh, contentDescription = null)
            }
        }

        if (showBackButton) {
            val stack = LocalNavBackStack.current
            IconButton(
                onClick = { stack.removeLastOrNullWorkaround() },
                modifier = Modifier.align(Alignment.TopStart).padding(16.dp),
            ) {
                Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
            }
        }
    }
}

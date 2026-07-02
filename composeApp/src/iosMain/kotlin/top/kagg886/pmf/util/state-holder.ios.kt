package top.kagg886.pmf.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.SaveableStateHolder
import androidx.compose.runtime.saveable.rememberSaveableStateHolder

@Composable
actual fun rememberReferenceSavableStateHolder(): SaveableStateHolder = rememberSaveableStateHolder()

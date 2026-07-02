@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package top.kagg886.pmf.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.SaveableStateHolder
import co.touchlab.kermit.Logger

private val logger = Logger.withTag("SaveableStateHolder")

/**
 * ================================================
 * Author:     iveou
 * Created on: 2026/7/1 17:43
 * ================================================
 */

@Composable
public expect fun rememberReferenceSavableStateHolder(): SaveableStateHolder

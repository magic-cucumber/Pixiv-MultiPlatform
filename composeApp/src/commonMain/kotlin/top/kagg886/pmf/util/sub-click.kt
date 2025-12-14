package top.kagg886.pmf.util

import androidx.compose.ui.Modifier

/**
 * ================================================
 * Author:     886kagg
 * Created on: 2025/12/14 14:25
 * ================================================
 */

/**
 * 添加副点击事件：
 *
 * 在电脑平台为右键检测
 * 在手机平台为长按检测
 */
expect fun Modifier.onSubClick(onSubClick: () -> Unit): Modifier

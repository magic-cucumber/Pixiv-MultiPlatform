package top.kagg886.pmf.util

/**
 * ================================================
 * Author:     iveou
 * Created on: 2026/7/27 14:11
 * ================================================
 */

class SnackBarAction {
    var message: String = ""
        private set

    var actionLabel: String? = null
        private set

    var onAction: (() -> Unit)? = null
        private set

    fun message(message: String): Unit {
        this.message = message
    }

    fun action(label: String, click: () -> Unit = {}): Unit {
        actionLabel = label
        onAction = click
    }
}

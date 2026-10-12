package com.plugin.floatingwindow

import app.tauri.annotation.InvokeArg

@InvokeArg
class NotificationArgs {
    var title: String? = null
    var body: String? = null
    var channelName: String? = null
    var channelDescription: String? = null
}

internal data class NotificationText(
    val title: String,
    val body: String,
    val channelName: String,
    val channelDescription: String
) {
    companion object {
        fun resolve(appName: String, args: NotificationArgs? = null): NotificationText {
            fun String?.orDefault(default: String) = this?.takeIf { it.isNotBlank() } ?: default
            return NotificationText(
                args?.title.orDefault(appName),
                args?.body.orDefault("Floating window is running. Tap to return to $appName."),
                args?.channelName.orDefault("$appName floating windows"),
                args?.channelDescription.orDefault("Keeps floating windows active while in use.")
            )
        }
    }
}

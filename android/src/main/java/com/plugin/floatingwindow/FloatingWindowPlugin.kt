package com.plugin.floatingwindow

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.view.View
import app.tauri.annotation.Command
import app.tauri.annotation.InvokeArg
import app.tauri.annotation.TauriPlugin
import app.tauri.plugin.Invoke
import app.tauri.plugin.JSObject
import app.tauri.plugin.Plugin
import com.petterp.floatingx.FloatingX
import com.petterp.floatingx.assist.FxScopeType

@InvokeArg
class CreateArgs {
    var tag: String? = null
    var width: Double? = null
    var height: Double? = null
    var x: Double? = null
    var y: Double? = null
}

@InvokeArg
class TagArgs {
    var tag: String? = null
}

@InvokeArg
class MoveArgs {
    var tag: String? = null
    var x: Double? = null
    var y: Double? = null
    var animated: Boolean? = null
}

@TauriPlugin
class FloatingWindowPlugin(private val activity: Activity) : Plugin(activity) {

    companion object {
        private const val DEFAULT_TAG = "default"
        private const val CLICK_EVENT = "floating-window-click"
    }

    @Command
    fun check_permission(invoke: Invoke) {
        val granted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(activity)
        } else {
            true
        }
        val ret = JSObject()
        ret.put("granted", granted)
        invoke.resolve(ret)
    }

    @Command
    fun request_permission(invoke: Invoke) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(activity)) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${activity.packageName}")
            )
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            activity.startActivity(intent)
        }
        val ret = JSObject()
        ret.put("granted", Settings.canDrawOverlays(activity))
        invoke.resolve(ret)
    }

    @Command
    fun create(invoke: Invoke) {
        try {
            val args = invoke.parseArgs(CreateArgs::class.java)
            val tag = args.tag ?: DEFAULT_TAG

            val control = FloatingX.install {
                setContext(activity.application)
                setLayout(R.layout.fx_default)
                setTag(tag)
                setScopeType(FxScopeType.SYSTEM)
            }

            control.setClickListener(View.OnClickListener {
                val data = JSObject()
                data.put("tag", tag)
                trigger(CLICK_EVENT, data)
            })

            control.show()

            val ret = JSObject()
            ret.put("tag", tag)
            invoke.resolve(ret)
        } catch (e: Exception) {
            invoke.reject(e.message ?: "Failed to create floating window")
        }
    }

    @Command
    fun show(invoke: Invoke) {
        try {
            val args = invoke.parseArgs(TagArgs::class.java)
            val tag = args.tag ?: DEFAULT_TAG
            FloatingX.control(tag).show()
            invoke.resolve()
        } catch (e: Exception) {
            invoke.reject(e.message ?: "Failed to show floating window")
        }
    }

    @Command
    fun hide(invoke: Invoke) {
        try {
            val args = invoke.parseArgs(TagArgs::class.java)
            val tag = args.tag ?: DEFAULT_TAG
            FloatingX.control(tag).hide()
            invoke.resolve()
        } catch (e: Exception) {
            invoke.reject(e.message ?: "Failed to hide floating window")
        }
    }

    @Command
    fun move_window(invoke: Invoke) {
        try {
            val args = invoke.parseArgs(MoveArgs::class.java)
            val tag = args.tag ?: DEFAULT_TAG
            val x = args.x?.toFloat() ?: 0f
            val y = args.y?.toFloat() ?: 0f
            val animated = args.animated ?: true
            FloatingX.control(tag).move(x, y, animated)
            invoke.resolve()
        } catch (e: Exception) {
            invoke.reject(e.message ?: "Failed to move floating window")
        }
    }

    @Command
    fun destroy(invoke: Invoke) {
        try {
            val args = invoke.parseArgs(TagArgs::class.java)
            val tag = args.tag ?: DEFAULT_TAG
            FloatingX.control(tag).cancel()
            invoke.resolve()
        } catch (e: Exception) {
            invoke.reject(e.message ?: "Failed to destroy floating window")
        }
    }
}

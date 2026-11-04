package com.plugin.floatingwindow

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.window.OnBackInvokedCallback
import android.window.OnBackInvokedDispatcher
import org.json.JSONArray
import org.json.JSONObject

/** A separate modal overlay. Its size does not enlarge the pet's touch window.
 * Only IDs cross back to JS; this class knows no action URLs or host commands. */
internal class PetOverlayMenu(context: Context, private val workArea: () -> Rect) {
    private val context = ContextThemeWrapper(context.applicationContext, android.R.style.Theme_Material_Light_Dialog_Alert)
    private val density = context.resources.displayMetrics.density
    private var dialog: Dialog? = null
    private var menuId: String? = null

    fun close(id: String? = null) {
        if (id != null && id != menuId) return
        dialog?.dismiss()
        dialog = null
        menuId = null
    }

    fun show(id: String, tree: JSONArray, select: (String) -> Unit) {
        close()
        val area = workArea()
        val windowDialog = Dialog(context)
        val path = mutableListOf<Pair<String, JSONArray>>()
        path.add("宠物菜单" to tree)
        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(8), dp(12), dp(12))
            background = GradientDrawable().apply { setColor(Color.WHITE); cornerRadius = dp(18).toFloat() }
        }
        val header = LinearLayout(context).apply { gravity = Gravity.CENTER_VERTICAL }
        val back = Button(context).apply { text = "返回" }
        val title = TextView(context).apply { textSize = 17f; setTextColor(Color.DKGRAY) }
        val dismiss = Button(context).apply { text = "关闭"; setOnClickListener { close(id) } }
        header.addView(back)
        header.addView(title, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        header.addView(dismiss)
        val maxListHeight = minOf(dp(420), area.height() - dp(120)).coerceAtLeast(dp(48))
        val scroll = object : ScrollView(context) {
            override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
                val parentLimit = if (View.MeasureSpec.getMode(heightMeasureSpec) == View.MeasureSpec.UNSPECIFIED)
                    maxListHeight else View.MeasureSpec.getSize(heightMeasureSpec)
                super.onMeasure(widthMeasureSpec, View.MeasureSpec.makeMeasureSpec(minOf(maxListHeight, parentLimit), View.MeasureSpec.AT_MOST))
            }
        }
        val rows = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(rows)
        content.addView(header)
        content.addView(scroll)

        fun render() {
            title.text = path.last().first
            back.visibility = if (path.size > 1) View.VISIBLE else View.GONE
            rows.removeAllViews()
            val nodes = path.last().second
            for (index in 0 until nodes.length()) {
                val node = nodes.getJSONObject(index)
                if (node.optString("kind") == "separator") continue
                val branch = node.optString("kind") == "branch"
                val row = TextView(context).apply {
                    text = node.getString("label") + if (branch) "  ›" else ""
                    textSize = 16f
                    gravity = Gravity.CENTER_VERTICAL
                    minHeight = dp(48)
                    setPadding(dp(12), dp(12), dp(12), dp(12))
                    setTextColor(Color.DKGRAY)
                    isEnabled = node.optBoolean("enabled", true)
                    alpha = if (isEnabled) 1f else 0.4f
                    isFocusable = true
                    setBackgroundResource(android.R.drawable.list_selector_background)
                }
                row.setOnClickListener {
                    if (branch) {
                        path.add(node.getString("label") to node.getJSONArray("children"))
                        render()
                    } else {
                        close(id)
                        select(node.getString("id"))
                    }
                }
                rows.addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            }
            scroll.scrollTo(0, 0)
        }
        back.setOnClickListener { if (path.size > 1) { path.removeAt(path.lastIndex); render() } }
        windowDialog.setOnKeyListener { _, code, event ->
            if (code == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP && path.size > 1) {
                path.removeAt(path.lastIndex); render(); true
            } else false
        }
        windowDialog.setContentView(content)
        windowDialog.setCanceledOnTouchOutside(true)
        val window = checkNotNull(windowDialog.window)
        window.setType(PetOverlayWindow.overlayType())
        window.setBackgroundDrawableResource(android.R.color.transparent)
        window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        window.attributes = window.attributes.apply { dimAmount = 0.15f }
        dialog = windowDialog
        menuId = id
        render()
        windowDialog.show()
        window.setLayout(minOf(dp(360), (area.width() - dp(24)).coerceAtLeast(dp(120))), ViewGroup.LayoutParams.WRAP_CONTENT)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val callback = OnBackInvokedCallback {
                if (path.size > 1) { path.removeAt(path.lastIndex); render() } else close(id)
            }
            windowDialog.onBackInvokedDispatcher.registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_DEFAULT, callback)
            windowDialog.setOnDismissListener {
                windowDialog.onBackInvokedDispatcher.unregisterOnBackInvokedCallback(callback)
            }
        }
    }

    private fun dp(value: Int) = (value * density).toInt()
}

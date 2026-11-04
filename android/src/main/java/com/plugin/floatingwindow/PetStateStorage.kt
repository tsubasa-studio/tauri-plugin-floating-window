package com.plugin.floatingwindow

import android.content.Context
import org.json.JSONObject

/** Main WebView plugin calls and overlay JS calls use the same lock and storage. */
internal class PetStateStorage(context: Context) {
    private val preferences = context.getSharedPreferences("pet-runtime-state", Context.MODE_PRIVATE)
    companion object { private val lock = Any() }
    private fun validate(scope: String) {
        require(scope.matches(Regex("[A-Za-z0-9_-]{1,128}"))) { "Invalid pet state scope" }
    }
    fun read(scope: String): String = synchronized(lock) {
        validate(scope)
        preferences.getString(scope, "") ?: ""
    }
    fun compareAndSet(scope: String, expected: String, next: String): Boolean = synchronized(lock) {
        validate(scope)
        if ((preferences.getString(scope, "") ?: "") != expected) return@synchronized false
        JSONObject(next)
        check(preferences.edit().putString(scope, next).commit()) { "Pet state persistence failed" }
        true
    }
}

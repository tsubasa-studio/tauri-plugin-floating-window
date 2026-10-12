package com.plugin.floatingwindow

import java.io.File

/** Resolves files from the extracted release bundle without rewriting HTML entry points. */
internal object BundledAssets {
    fun resolve(root: File, requestedPath: String, isMainFrame: Boolean, accept: String): File? {
        val canonicalRoot = root.canonicalFile
        val path = requestedPath.trimStart('/')
        // Invalid paths must not become successful SPA navigations either.
        if (path.split('/', '\\').any { it == ".." }) return null
        fun file(relativePath: String): File? {
            val candidate = File(canonicalRoot, relativePath).canonicalFile
            return candidate.takeIf {
                it.isFile && it.path.startsWith(canonicalRoot.path + File.separator)
            }
        }
        file(path.ifEmpty { "index.html" })?.let { return it }
        val htmlNavigation = isMainFrame &&
            (accept.isBlank() || accept.contains("text/html", ignoreCase = true))
        val isSpaRoute = !path.substringAfterLast('/').contains('.')
        return if (htmlNavigation && isSpaRoute) file("index.html") else null
    }
}

package com.plugin.floatingwindow

import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class BundledAssetsTest {
    @get:Rule val directory = TemporaryFolder()

    @Test fun servesIndependentHtmlEntriesBeforeSpaFallback() {
        val root = directory.root
        File(root, "index.html").writeText("main entry")
        File(root, "floating.html").writeText("floating entry")
        assertEquals("floating entry", BundledAssets.resolve(root, "/floating.html", true, "text/html")?.readText())
        assertEquals("main entry", BundledAssets.resolve(root, "/", true, "text/html")?.readText())
        assertEquals("main entry", BundledAssets.resolve(root, "/pets/123", true, "text/html")?.readText())
        assertEquals("floating entry", BundledAssets.resolve(root, "/floating.html", false, "text/html")?.readText())
    }

    @Test fun missingFilesAndSubresourcesDoNotBecomeHtml() {
        File(directory.root, "index.html").writeText("main entry")
        for (path in listOf("/missing.html", "/missing.js", "/movie.mp4", "../secret", "a/../../secret")) {
            assertNull(path, BundledAssets.resolve(directory.root, path, true, "text/html"))
        }
        assertNull(BundledAssets.resolve(directory.root, "/api", false, "text/html"))
        assertNull(BundledAssets.resolve(directory.root, "/api", true, "application/json"))
    }

    @Test fun builtExampleRetainsBothHtmlEntryPoints() {
        val root = File(checkNotNull(System.getProperty("floatingWindowAssets")))
        val main = BundledAssets.resolve(root, "/index.html", true, "text/html")
        val floating = BundledAssets.resolve(root, "/floating.html", true, "text/html")
        assertNotNull("Build examples/tauri-app before running this test", main)
        assertNotNull("floating.html must be included in the release frontend bundle", floating)
        assertNotEquals(main!!.canonicalPath, floating!!.canonicalPath)
        assertNotEquals(main.readText(), floating.readText())
        assertTrue(floating.readText().contains("AndroidFloatingWindow.close"))
    }
}

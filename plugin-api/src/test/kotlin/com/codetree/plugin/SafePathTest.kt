package com.codetree.plugin

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SafePathTest {

    private fun normalized(raw: String): String {
        val check = SafePath.checkRelative(raw)
        assertTrue(check is SafePath.Check.Ok, "expected Ok for '$raw', got $check")
        return (check as SafePath.Check.Ok).normalized
    }

    private fun rejected(raw: String): String {
        val check = SafePath.checkRelative(raw)
        assertTrue(check is SafePath.Check.Rejected, "expected Rejected for '$raw', got $check")
        return (check as SafePath.Check.Rejected).reason
    }

    @Test
    fun `plain relative path is accepted`() {
        assertEquals("content/topics.json", normalized("content/topics.json"))
    }

    @Test
    fun `backslashes are normalized to slashes`() {
        assertEquals("content/topics.json", normalized("content\\topics.json"))
    }

    @Test
    fun `dot segments and empty segments are collapsed`() {
        assertEquals("a/b", normalized("./a/./b"))
        assertEquals("a/b", normalized("a//b"))
    }

    @Test
    fun `absolute paths are rejected`() {
        assertEquals("absolute path", rejected("/etc/passwd"))
        assertEquals("drive path", rejected("C:/Windows/system32"))
        assertEquals("drive path", rejected("D:\\evil.txt"))
    }

    @Test
    fun `parent traversal is rejected regardless of position`() {
        assertEquals("parent traversal", rejected("../x"))
        assertEquals("parent traversal", rejected("a/../../b"))
        assertEquals("parent traversal", rejected("a/.."))
    }

    @Test
    fun `nul byte is rejected`() {
        assertEquals("nul byte", rejected("a\u0000b"))
    }

    @Test
    fun `empty and blank paths are rejected`() {
        assertEquals("empty path", rejected(""))
        assertEquals("empty path", rejected("   "))
        assertEquals("empty path", rejected("./"))
        assertEquals("empty path", rejected("."))
    }

    @Test
    fun `overlong segment is rejected`() {
        assertEquals("segment too long", rejected("a".repeat(256)))
        assertTrue(rejected("a".repeat(256)).isNotBlank())
    }

    @Test
    fun `isUnderRoot accepts root itself and children only`() {
        assertTrue(SafePath.isUnderRoot("/data/workspace", "/data/workspace"))
        assertTrue(SafePath.isUnderRoot("/data/workspace", "/data/workspace/a.txt"))
        assertFalse(SafePath.isUnderRoot("/data/workspace", "/data/workspace-other/a.txt"))
        assertFalse(SafePath.isUnderRoot("/data/workspace", "/data/workspacex/a.txt"))
        assertFalse(SafePath.isUnderRoot("/data/workspace/", "/etc/passwd"))
    }

    @Test
    fun `forbidden entries cover executables and lib directories`() {
        for (path in listOf(
            "evil.so",
            "app.dex",
            "lib/dex.jar",
            "classes/Main.class",
            "scripts/run.sh",
            "tool.bat",
            "native.elf",
            "module.wasm",
            "app.apk",
            "blob.bin",
        )) {
            assertTrue(SafePath.isForbiddenEntry(path), "expected forbidden: $path")
        }
    }

    @Test
    fun `normal content files are not forbidden`() {
        for (path in listOf("content/topics.json", "syntax/python.json", "theme/tokens.json", "assets/logo.png", "LICENSE")) {
            assertFalse(SafePath.isForbiddenEntry(path), "expected allowed: $path")
        }
    }

    @Test
    fun `normalization result is stable for already normalized paths`() {
        val once = normalized("content/examples.json")
        val twice = normalized(once)
        assertEquals(once, twice)
    }
}

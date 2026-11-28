package com.codetree.plugin

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SemVerTest {

    @Test
    fun `plain semver parses`() {
        assertEquals(SemVer(1, 2, 3), SemVer.parse("1.2.3"))
        assertEquals(SemVer(0, 0, 1), SemVer.parse("0.0.1"))
        assertEquals(SemVer(10, 20, 30), SemVer.parse(" 10.20.30 "))
    }

    @Test
    fun `v prefix is accepted`() {
        assertEquals(SemVer(2, 0, 0), SemVer.parse("v2.0.0"))
    }

    @Test
    fun `invalid versions are rejected`() {
        for (text in listOf("", "1.2", "1.2.3.4", "1.x.3", "v", "latest", "1.2.3-beta", "-1.2.3")) {
            assertNull(SemVer.parse(text), "expected null for '$text'")
        }
    }

    @Test
    fun `version comparison orders by major minor patch`() {
        assertTrue(SemVer(1, 0, 0) < SemVer(2, 0, 0))
        assertTrue(SemVer(1, 2, 0) < SemVer(1, 3, 0))
        assertTrue(SemVer(1, 2, 3) < SemVer(1, 2, 4))
        assertEquals(0, SemVer(1, 2, 3).compareTo(SemVer(1, 2, 3)))
    }

    @Test
    fun `version toString round trips`() {
        assertEquals("1.2.3", SemVer(1, 2, 3).toString())
        assertEquals("1.2.3", SemVer.parse(SemVer(1, 2, 3).toString())?.toString())
    }

    @Test
    fun `range with lower and upper bounds`() {
        val range = VersionRange(">=1.0.0 <2.0.0")
        assertTrue(range.contains(SemVer(1, 0, 0)))
        assertTrue(range.contains(SemVer(1, 5, 2)))
        assertFalse(range.contains(SemVer(0, 9, 9)))
        assertFalse(range.contains(SemVer(2, 0, 0)))
    }

    @Test
    fun `range supports inclusive and exclusive operators`() {
        assertTrue(VersionRange(">1.0.0").contains(SemVer(1, 0, 1)))
        assertFalse(VersionRange(">1.0.0").contains(SemVer(1, 0, 0)))
        assertTrue(VersionRange("<=1.0.0").contains(SemVer(1, 0, 0)))
        assertFalse(VersionRange("<=1.0.0").contains(SemVer(1, 0, 1)))
        assertTrue(VersionRange("=1.0.0").contains(SemVer(1, 0, 0)))
        assertFalse(VersionRange("=1.0.0").contains(SemVer(1, 0, 1)))
    }

    @Test
    fun `empty range matches every version`() {
        assertTrue(VersionRange("").contains(SemVer(0, 0, 0)))
        assertTrue(VersionRange("   ").contains(SemVer(9, 9, 9)))
    }

    @Test
    fun `invalid range throws`() {
        assertThrows(IllegalArgumentException::class.java) { VersionRange(">=1.0") }
        assertThrows(IllegalArgumentException::class.java) { VersionRange(">=abc") }
    }

    @Test
    fun `matches helper combines parse and range`() {
        assertTrue(VersionRange.matches(">=1.0.0 <2.0.0", "1.7.3"))
        assertFalse(VersionRange.matches(">=1.0.0 <2.0.0", "2.1.0"))
        assertFalse(VersionRange.matches(">=1.0.0", "not-a-version"))
    }
}

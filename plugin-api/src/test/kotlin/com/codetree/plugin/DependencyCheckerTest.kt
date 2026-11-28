package com.codetree.plugin

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DependencyCheckerTest {

    private val checker = DependencyChecker()

    private fun manifest(
        packId: String = "new.pack",
        version: String = "1.0.0",
        dependencies: List<PackDependency> = emptyList(),
    ): PackManifest = PackManifest(
        packId = packId,
        packType = "knowledge",
        version = version,
        title = LocalizedText.of("新包", "New Pack"),
        locales = listOf("zh-CN"),
        dependencies = dependencies,
    )

    private fun codes(result: CodepackValidationResult): List<String> = result.issues.map { it.code }

    private fun assertCode(code: String, result: CodepackValidationResult) {
        assertTrue(codes(result).contains(code), "expected code '$code', got ${codes(result)}")
    }

    private fun summary(
        packId: String,
        version: String,
        dependencies: List<PackDependency> = emptyList(),
    ): DependencyChecker.InstalledPackSummary =
        DependencyChecker.InstalledPackSummary(packId, version, dependencies)

    @Test
    fun `missing required dependency blocks install`() {
        val result = checker.check(
            manifest(dependencies = listOf(PackDependency("core.base", ">=1.0.0 <2.0.0"))),
            installed = emptyMap<String, String>(),
        )
        assertFalse(result.ok)
        assertCode("dependency.missing", result)
    }

    @Test
    fun `missing optional dependency is only a warning`() {
        val result = checker.check(
            manifest(dependencies = listOf(PackDependency("core.extra", optional = true))),
            installed = emptyMap<String, String>(),
        )
        assertTrue(result.ok)
        assertCode("dependency.optional_missing", result)
    }

    @Test
    fun `satisfied dependency passes`() {
        val result = checker.check(
            manifest(dependencies = listOf(PackDependency("core.base", ">=1.0.0 <2.0.0"))),
            installed = mapOf("core.base" to "1.5.0"),
        )
        assertTrue(result.ok, "unexpected errors: ${result.issues}")
    }

    @Test
    fun `installed version outside range conflicts`() {
        val result = checker.check(
            manifest(dependencies = listOf(PackDependency("core.base", ">=1.0.0 <2.0.0"))),
            installed = mapOf("core.base" to "2.0.0"),
        )
        assertCode("dependency.version_conflict", result)
    }

    @Test
    fun `dependency cycle is reported with the chain`() {
        val result = checker.check(
            manifest(packId = "a.pack", dependencies = listOf(PackDependency("b.pack"))),
            installedPacks = listOf(summary("b.pack", "1.0.0", listOf(PackDependency("a.pack")))),
        )
        assertCode("dependency.cycle", result)
        val cycle = result.errors.first { it.code == "dependency.cycle" }
        assertTrue(cycle.message.contains("a.pack"), cycle.message)
        assertTrue(cycle.message.contains("b.pack"), cycle.message)
    }

    @Test
    fun `self dependency is rejected`() {
        val result = checker.check(
            manifest(packId = "self.pack", dependencies = listOf(PackDependency("self.pack"))),
            installed = emptyMap<String, String>(),
        )
        assertCode("dependency.self_reference", result)
    }

    @Test
    fun `installing the same version twice is a conflict`() {
        val result = checker.check(
            manifest(packId = "core.python", version = "1.0.0"),
            installed = mapOf("core.python" to "1.0.0"),
        )
        assertCode("dependency.duplicate_pack", result)
        assertFalse(result.ok)
    }

    @Test
    fun `installing a different version is treated as an update warning`() {
        val result = checker.check(
            manifest(packId = "core.python", version = "2.0.0"),
            installed = mapOf("core.python" to "1.0.0"),
        )
        assertTrue(result.ok)
        assertCode("dependency.update_pack", result)
    }

    @Test
    fun `invalid version range is reported`() {
        val result = checker.check(
            manifest(dependencies = listOf(PackDependency("core.base", ">=1.0"))),
            installed = mapOf("core.base" to "1.0.0"),
        )
        assertCode("dependency.invalid_range", result)
    }

    @Test
    fun `invalid dependency pack id is reported`() {
        val result = checker.check(
            manifest(dependencies = listOf(PackDependency("Bad.Pack"))),
            installed = emptyMap<String, String>(),
        )
        assertCode("dependency.invalid_pack_id", result)
    }

    @Test
    fun `duplicate dependency declaration is reported`() {
        val result = checker.check(
            manifest(
                dependencies = listOf(
                    PackDependency("core.base", ">=1.0.0"),
                    PackDependency("core.base", ">=1.0.0 <2.0.0"),
                ),
            ),
            installed = mapOf("core.base" to "1.2.0"),
        )
        assertCode("dependency.duplicate", result)
    }

    @Test
    fun `transitive installed chain without cycle passes`() {
        val result = checker.check(
            manifest(dependencies = listOf(PackDependency("b.pack"))),
            installedPacks = listOf(
                summary("b.pack", "1.1.0", listOf(PackDependency("c.pack"))),
                summary("c.pack", "1.0.0"),
            ),
        )
        assertTrue(result.ok, "unexpected errors: ${result.issues}")
    }

    @Test
    fun `invalid installed dependency version is reported`() {
        val result = checker.check(
            manifest(dependencies = listOf(PackDependency("core.base"))),
            installed = mapOf("core.base" to "not-semver"),
        )
        assertCode("dependency.invalid_version", result)
    }

    @Test
    fun `result carries manifest for UI display`() {
        val input = manifest()
        val result = checker.check(input, installed = mapOf<String, String>())
        assertEquals(input, result.manifest)
        assertEquals(listOf<String>(), codes(result))
    }
}

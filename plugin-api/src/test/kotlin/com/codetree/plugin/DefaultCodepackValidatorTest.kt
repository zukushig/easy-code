package com.codetree.plugin

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DefaultCodepackValidatorTest {

    private val validator = DefaultCodepackValidator()
    private val validHash = "a".repeat(64)
    private val otherHash = "b".repeat(64)

    // ------------------------------------------------------------------ fixture

    private fun manifest(
        packId: String = "core.python",
        packType: String = "knowledge",
        version: String = "1.0.0",
        title: LocalizedText = LocalizedText.of("Python 基础", "Python Basics"),
        locales: List<String> = listOf("zh-CN", "en"),
        defaultLocale: String = "zh-CN",
        schemaVersion: Int = PackManifest.SCHEMA_VERSION,
        pluginApiVersion: Int = PackManifest.PLUGIN_API_VERSION,
        minAppVersion: String = "0.1.0",
        entry: String = "content/topics.json",
        permissions: List<String> = emptyList(),
        contributions: List<PluginContribution> = emptyList(),
        dependencies: List<PackDependency> = emptyList(),
        files: List<PackFile> = listOf(PackFile("content/topics.json", validHash, 10L)),
    ): PackManifest = PackManifest(
        schemaVersion = schemaVersion,
        packId = packId,
        packType = packType,
        version = version,
        title = title,
        defaultLocale = defaultLocale,
        locales = locales,
        minAppVersion = minAppVersion,
        pluginApiVersion = pluginApiVersion,
        entry = entry,
        permissions = permissions,
        contributions = contributions,
        dependencies = dependencies,
        files = files,
    )

    private fun json(manifest: PackManifest): String =
        ContentJson.encodeToString(PackManifest.serializer(), manifest)

    private fun codes(result: CodepackValidationResult): List<String> = result.issues.map { it.code }

    private fun assertCode(code: String, result: CodepackValidationResult) {
        assertTrue(codes(result).contains(code), "expected code '$code', got ${codes(result)}")
    }

    private fun parse(manifest: PackManifest): CodepackValidationResult =
        validator.parseAndValidateManifest(json(manifest))

    private fun entriesResult(
        manifest: PackManifest,
        entries: List<ZipEntryInfo>,
        actualHashes: Map<String, String?> = mapOf("content/topics.json" to validHash),
        limits: ValidationLimits = ValidationLimits(),
    ): CodepackValidationResult = validator.validateEntries(manifest, entries, actualHashes, limits)

    private fun manifestEntry(sizeBytes: Long = 50L): ZipEntryInfo =
        ZipEntryInfo("manifest.json", sizeBytes, compressedSizeBytes = sizeBytes)

    private fun contentEntry(
        path: String = "content/topics.json",
        sizeBytes: Long = 10L,
        compressedSizeBytes: Long = 10L,
    ): ZipEntryInfo = ZipEntryInfo(path, sizeBytes, compressedSizeBytes = compressedSizeBytes)

    // ------------------------------------------------------------------ 清单：合法样例

    @Test
    fun `valid knowledge manifest passes`() {
        val result = parse(manifest())
        assertTrue(result.ok, "unexpected errors: ${result.issues}")
        assertNotNull(result.manifest)
        assertTrue(result.warnings.isEmpty())
    }

    @Test
    fun `valid plugin manifest with allowed contributions passes`() {
        val result = parse(
            manifest(
                packType = "plugin",
                contributions = listOf(
                    PluginContribution("content", "content/topics.json"),
                    PluginContribution("syntax", "syntax/python.json"),
                    PluginContribution("theme", "theme/tokens.json"),
                ),
            ),
        )
        assertTrue(result.ok, "unexpected errors: ${result.issues}")
    }

    @Test
    fun `source manifest with empty files is allowed at parse stage`() {
        val result = parse(manifest(files = emptyList()))
        assertTrue(result.ok, "unexpected errors: ${result.issues}")
    }

    @Test
    fun `warnings do not block`() {
        val result = parse(manifest(locales = listOf("zh-CN", "en", "zh-CN")))
        assertTrue(result.ok)
        assertEquals(1, result.warnings.size)
        assertCode("manifest.duplicate_locale", result)
    }

    // ------------------------------------------------------------------ 清单：结构错误

    @Test
    fun `blank manifest is rejected`() {
        val result = validator.parseAndValidateManifest("   ")
        assertCode("manifest.missing", result)
        assertNull(result.manifest)
    }

    @Test
    fun `oversized manifest is rejected`() {
        val result = validator.parseAndValidateManifest(json(manifest()), ValidationLimits(maxManifestBytes = 16))
        assertCode("manifest.too_large", result)
    }

    @Test
    fun `invalid json is rejected`() {
        assertCode("manifest.invalid_json", validator.parseAndValidateManifest("{"))
        assertCode("manifest.invalid_json", validator.parseAndValidateManifest("[1,2,3]"))
    }

    @Test
    fun `missing required field is reported`() {
        val raw = """{"packType":"knowledge","version":"1.0.0"}"""
        val result = validator.parseAndValidateManifest(raw)
        assertCode("manifest.required_field", result)
        assertNull(result.manifest)
    }

    @Test
    fun `invalid field type is reported`() {
        val raw = """{"packId":123,"packType":"knowledge","version":"1.0.0"}"""
        assertCode("manifest.invalid_field", validator.parseAndValidateManifest(raw))
    }

    // ------------------------------------------------------------------ 清单：字段校验

    @Test
    fun `uppercase pack id is rejected`() {
        assertCode("manifest.invalid_pack_id", parse(manifest(packId = "Core.Python")))
    }

    @Test
    fun `overlong pack id is rejected`() {
        assertCode("manifest.invalid_pack_id", parse(manifest(packId = "a".repeat(121))))
    }

    @Test
    fun `invalid version is rejected`() {
        assertCode("manifest.invalid_version", parse(manifest(version = "1.0")))
        assertCode("manifest.invalid_version", parse(manifest(version = "latest")))
    }

    @Test
    fun `unknown pack type is rejected`() {
        assertCode("manifest.invalid_pack_type", parse(manifest(packType = "service")))
    }

    @Test
    fun `empty locales is rejected`() {
        assertCode("manifest.invalid_locales", parse(manifest(locales = emptyList())))
    }

    @Test
    fun `locales must contain default locale`() {
        assertCode("manifest.invalid_locales", parse(manifest(locales = listOf("en"))))
    }

    @Test
    fun `empty title is rejected`() {
        assertCode("manifest.missing_title", parse(manifest(title = LocalizedText.EMPTY)))
    }

    @Test
    fun `invalid schema and plugin api versions are rejected`() {
        assertCode("manifest.invalid_schema_version", parse(manifest(schemaVersion = 0)))
        assertCode("manifest.invalid_plugin_api_version", parse(manifest(pluginApiVersion = 0)))
    }

    @Test
    fun `invalid min app version is rejected`() {
        assertCode("manifest.invalid_min_app_version", parse(manifest(minAppVersion = "abc")))
    }

    @Test
    fun `unsafe entry path is rejected`() {
        assertCode("entry.parent_traversal", parse(manifest(entry = "../evil.json")))
        assertCode("entry.absolute_path", parse(manifest(entry = "/etc/evil.json")))
    }

    @Test
    fun `permissions are rejected in p0`() {
        assertCode("manifest.unknown_capability", parse(manifest(permissions = listOf("network"))))
    }

    @Test
    fun `unknown executable capability fields are rejected`() {
        val base = """{"schemaVersion":1,"packId":"core.python","packType":"knowledge","version":"1.0.0","title":{"zh-CN":"x"},"locales":["zh-CN"],"files":[]"""
        assertCode("manifest.unknown_capability", validator.parseAndValidateManifest("$base,\"entrypoints\":[\"main\"]}"))
        assertCode("manifest.unknown_capability", validator.parseAndValidateManifest("$base,\"scripts\":{\"post\":\"x\"}}"))
        assertCode("manifest.unknown_capability", validator.parseAndValidateManifest("$base,\"executable\":\"a.out\"}"))
    }

    // ------------------------------------------------------------------ 清单：文件完整性声明

    @Test
    fun `placeholder hash is rejected`() {
        val result = parse(manifest(files = listOf(PackFile("content/topics.json", "LOWERCASE_SHA256_HEX", 10L))))
        assertCode("file.placeholder_hash", result)
    }

    @Test
    fun `uppercase or short hash is rejected`() {
        assertCode("file.invalid_hash", parse(manifest(files = listOf(PackFile("content/topics.json", "A".repeat(64), 10L)))))
        assertCode("file.invalid_hash", parse(manifest(files = listOf(PackFile("content/topics.json", "abc", 10L)))))
    }

    @Test
    fun `unsafe file path is rejected`() {
        assertCode(
            "entry.parent_traversal",
            parse(manifest(files = listOf(PackFile("../evil.json", validHash, 10L)))),
        )
        assertCode(
            "entry.absolute_path",
            parse(manifest(files = listOf(PackFile("/etc/passwd", validHash, 10L)))),
        )
    }

    @Test
    fun `negative file size is rejected`() {
        assertCode("file.invalid_size", parse(manifest(files = listOf(PackFile("content/topics.json", validHash, -1L)))))
    }

    @Test
    fun `declared executable file is rejected`() {
        assertCode("entry.forbidden_type", parse(manifest(files = listOf(PackFile("run.sh", validHash, 1L)))))
        assertCode("entry.forbidden_type", parse(manifest(files = listOf(PackFile("lib/native.so", validHash, 1L)))))
    }

    @Test
    fun `duplicate declared file paths are rejected`() {
        val result = parse(
            manifest(
                files = listOf(
                    PackFile("content/topics.json", validHash, 10L),
                    PackFile("content/topics.json", otherHash, 20L),
                ),
            ),
        )
        assertCode("file.duplicate_path", result)
    }

    // ------------------------------------------------------------------ 清单：贡献类型

    @Test
    fun `plugin without contributions is rejected`() {
        assertCode("contrib.missing", parse(manifest(packType = "plugin")))
    }

    @Test
    fun `rejected contribution types are rejected`() {
        for (type in listOf("runtime", "native", "executable")) {
            val result = parse(manifest(packType = "plugin", contributions = listOf(PluginContribution(type, "x.json"))))
            assertCode("contrib.rejected_type", result)
        }
    }

    @Test
    fun `unknown contribution type is rejected`() {
        val result = parse(manifest(packType = "plugin", contributions = listOf(PluginContribution("widget", "x.json"))))
        assertCode("contrib.type_not_allowed", result)
    }

    @Test
    fun `unsafe contribution path is rejected`() {
        val result = parse(manifest(packType = "plugin", contributions = listOf(PluginContribution("content", "../x.json"))))
        assertCode("contrib.invalid_path", result)
    }

    // ------------------------------------------------------------------ 条目：合法样例

    @Test
    fun `valid entries pass integrity check`() {
        val result = entriesResult(
            manifest(),
            listOf(manifestEntry(), contentEntry()),
        )
        assertTrue(result.ok, "unexpected errors: ${result.issues}")
        assertNotNull(result.manifest)
    }

    @Test
    fun `interface method skips hash verification when hashes are not supplied`() {
        val result = validator.validateEntries(manifest(), listOf(manifestEntry(), contentEntry()))
        assertTrue(result.ok, "unexpected errors: ${result.issues}")
    }

    // ------------------------------------------------------------------ 条目：路径与安全

    @Test
    fun `parent traversal entry is rejected`() {
        assertCode(
            "entry.parent_traversal",
            entriesResult(manifest(), listOf(manifestEntry(), contentEntry(path = "../evil.txt"))),
        )
    }

    @Test
    fun `absolute and drive entry paths are rejected`() {
        assertCode(
            "entry.absolute_path",
            entriesResult(manifest(), listOf(manifestEntry(), contentEntry(path = "/etc/passwd"))),
        )
        assertCode(
            "entry.absolute_path",
            entriesResult(manifest(), listOf(manifestEntry(), contentEntry(path = "C:/evil"))),
        )
    }

    @Test
    fun `symbolic link entry is rejected`() {
        val entries = listOf(manifestEntry(), contentEntry(), ZipEntryInfo("link", 1L, isSymbolicLink = true))
        assertCode("entry.symlink", entriesResult(manifest(), entries))
    }

    @Test
    fun `executable entry is rejected`() {
        assertCode(
            "entry.forbidden_type",
            entriesResult(manifest(), listOf(manifestEntry(), contentEntry(path = "lib/evil.so"))),
        )
        assertCode(
            "entry.forbidden_type",
            entriesResult(manifest(), listOf(manifestEntry(), contentEntry(path = "tool.dex"))),
        )
    }

    @Test
    fun `duplicate entry path is rejected`() {
        val entries = listOf(
            manifestEntry(),
            contentEntry(),
            contentEntry(),
        )
        assertCode("entry.duplicate_path", entriesResult(manifest(), entries))
    }

    // ------------------------------------------------------------------ 条目：体积上限

    @Test
    fun `too many entries is rejected`() {
        val entries = listOf(manifestEntry(), contentEntry()) + (1..3).map { contentEntry(path = "assets/$it.json") }
        assertCode(
            "entry.too_many",
            entriesResult(manifest(), entries, limits = ValidationLimits(maxEntries = 2)),
        )
    }

    @Test
    fun `single file too large is rejected`() {
        assertCode(
            "entry.too_large",
            entriesResult(manifest(), listOf(manifestEntry(), contentEntry(sizeBytes = 100L)), limits = ValidationLimits(maxFileBytes = 10)),
        )
    }

    @Test
    fun `archive total too large is rejected`() {
        val manifestFiles = listOf(
            PackFile("content/a.json", validHash, 8L),
            PackFile("content/b.json", validHash, 8L),
        )
        val entries = listOf(
            manifestEntry(),
            contentEntry(path = "content/a.json", sizeBytes = 8L),
            contentEntry(path = "content/b.json", sizeBytes = 8L),
        )
        val result = entriesResult(
            manifest(files = manifestFiles),
            entries,
            actualHashes = mapOf("content/a.json" to validHash, "content/b.json" to validHash),
            limits = ValidationLimits(maxTotalBytes = 10),
        )
        assertCode("archive.too_large", result)
    }

    @Test
    fun `compression ratio bomb is rejected`() {
        val entries = listOf(
            manifestEntry(),
            contentEntry(sizeBytes = 10_000L, compressedSizeBytes = 10L),
        )
        assertCode(
            "archive.compression_ratio",
            entriesResult(manifest(), entries, limits = ValidationLimits(maxCompressionRatio = 10)),
        )
    }

    @Test
    fun `missing manifest entry is rejected`() {
        assertCode("manifest.missing", entriesResult(manifest(), listOf(contentEntry())))
    }

    @Test
    fun `manifest entry larger than limit is rejected`() {
        assertCode(
            "manifest.too_large",
            entriesResult(
                manifest(),
                listOf(manifestEntry(sizeBytes = 1000L), contentEntry()),
                limits = ValidationLimits(maxManifestBytes = 10),
            ),
        )
    }

    // ------------------------------------------------------------------ 条目：完整性

    @Test
    fun `missing declared file is rejected`() {
        assertCode("file.missing", entriesResult(manifest(), listOf(manifestEntry())))
    }

    @Test
    fun `unlisted actual file is rejected`() {
        val entries = listOf(manifestEntry(), contentEntry(), contentEntry(path = "extra.txt", sizeBytes = 1L))
        assertCode("file.unlisted", entriesResult(manifest(), entries))
    }

    @Test
    fun `size mismatch is rejected`() {
        assertCode(
            "file.size_mismatch",
            entriesResult(manifest(), listOf(manifestEntry(), contentEntry(sizeBytes = 99L))),
        )
    }

    @Test
    fun `sha mismatch is rejected`() {
        assertCode(
            "file.sha256_mismatch",
            entriesResult(
                manifest(),
                listOf(manifestEntry(), contentEntry()),
                actualHashes = mapOf("content/topics.json" to otherHash),
            ),
        )
    }

    @Test
    fun `placeholder hash in manifest is rejected during integrity check`() {
        val placeholderManifest = manifest(files = listOf(PackFile("content/topics.json", "LOWERCASE_SHA256_HEX", 10L)))
        assertCode(
            "file.placeholder_hash",
            entriesResult(placeholderManifest, listOf(manifestEntry(), contentEntry())),
        )
    }

    @Test
    fun `missing measured hash is rejected when hashes are supplied`() {
        assertCode(
            "file.checksum_missing",
            entriesResult(
                manifest(),
                listOf(manifestEntry(), contentEntry()),
                actualHashes = mapOf("content/topics.json" to null),
            ),
        )
    }

    @Test
    fun `empty manifest files cannot be installed`() {
        assertCode("file.missing", entriesResult(manifest(files = emptyList()), listOf(manifestEntry())))
    }

    @Test
    fun `signature directory only produces warning`() {
        val entries = listOf(
            manifestEntry(),
            contentEntry(),
            ZipEntryInfo("signatures/pack.sig", 5L, compressedSizeBytes = 5L),
        )
        val result = entriesResult(manifest(), entries)
        assertTrue(result.ok, "unexpected errors: ${result.issues}")
        assertCode("signature.present_but_ignored", result)
    }

    // ------------------------------------------------------------------ 兼容性

    @Test
    fun `compatible manifest passes`() {
        val result = validator.validateCompatibility(manifest(), appVersion = "0.1.0")
        assertTrue(result.ok, "unexpected errors: ${result.issues}")
    }

    @Test
    fun `newer schema version is incompatible`() {
        val result = validator.validateCompatibility(manifest(schemaVersion = PackManifest.SCHEMA_VERSION + 1))
        assertCode("compat.schema_version", result)
        assertFalse(result.ok)
    }

    @Test
    fun `newer plugin api version is incompatible`() {
        val result = validator.validateCompatibility(manifest(pluginApiVersion = PackManifest.PLUGIN_API_VERSION + 1))
        assertCode("compat.plugin_api", result)
    }

    @Test
    fun `higher min app version is incompatible`() {
        val result = validator.validateCompatibility(manifest(minAppVersion = "9.9.9"), appVersion = "0.1.0")
        assertCode("compat.min_app_version", result)
    }

    @Test
    fun `invalid app version from caller is reported`() {
        val result = validator.validateCompatibility(manifest(), appVersion = "not-a-version")
        assertCode("compat.invalid_app_version", result)
    }

    @Test
    fun `invalid min app version is reported by compatibility check`() {
        val result = validator.validateCompatibility(manifest(minAppVersion = "abc"))
        assertCode("manifest.invalid_min_app_version", result)
    }

    @Test
    fun `issues carry package relative paths`() {
        val result = parse(manifest(files = listOf(PackFile("../evil.json", validHash, 1L))))
        val issue = result.issues.first { it.code == "entry.parent_traversal" }
        assertEquals("../evil.json", issue.path)
        assertTrue(issue.message.isNotBlank())
    }
}

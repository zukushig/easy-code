package com.codetree.plugin

import kotlinx.serialization.MissingFieldException
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Codepack 校验器默认实现（docs/specs/plugin-api.md §3/§5/§7）。
 *
 * 设计约束：
 * - 纯函数：不触碰文件系统、不解压、不执行任何内容；ZIP 条目元信息由调用方（数据层）提供。
 * - 一次调用返回全部问题（不提前抛异常），`errors` 非空即阻断安装；WARNING 只展示不阻断。
 * - `CodepackValidator` 接口签名保持不变；SHA-256 实测值通过重载
 *   [validateEntries] 的 `actualHashes` 参数传入（path -> 64 位小写 hex）。
 *
 * 校验顺序与错误码（稳定，供 UI 与日志断言）：
 * 1. 清单：`manifest.missing` / `manifest.too_large` / `manifest.invalid_json` /
 *    `manifest.required_field` / `manifest.invalid_field` /
 *    `manifest.invalid_pack_id` / `manifest.invalid_version` / `manifest.invalid_pack_type` /
 *    `manifest.invalid_schema_version` / `manifest.invalid_plugin_api_version` /
 *    `manifest.invalid_min_app_version` / `manifest.invalid_locales` / `manifest.missing_title` /
 *    `manifest.invalid_entry` / `manifest.unknown_capability` / `contrib.*` / `file.*`。
 * 2. 条目：`entry.too_many` / `entry.too_large` / `archive.too_large` / `archive.compression_ratio` /
 *    `entry.absolute_path` / `entry.parent_traversal` / `entry.invalid_path` / `entry.symlink` /
 *    `entry.forbidden_type` / `entry.duplicate_path` / `entry.invalid_size`。
 * 3. 完整性：`file.missing` / `file.unlisted` / `file.size_mismatch` /
 *    `file.sha256_mismatch` / `file.checksum_missing` / `file.placeholder_hash` / `file.invalid_hash`。
 * 4. 兼容性：`compat.schema_version` / `compat.plugin_api` / `compat.min_app_version`。
 */
class DefaultCodepackValidator : CodepackValidator {

    override fun parseAndValidateManifest(
        manifestJson: String,
        limits: ValidationLimits,
    ): CodepackValidationResult {
        val issues = mutableListOf<ValidationIssue>()

        if (manifestJson.isBlank()) {
            issues += error("manifest.json", "manifest.missing", "清单为空")
            return CodepackValidationResult(manifest = null, issues = issues)
        }
        val manifestBytes = manifestJson.toByteArray(Charsets.UTF_8).size
        if (manifestBytes > limits.maxManifestBytes) {
            issues += error(
                "manifest.json",
                "manifest.too_large",
                "清单 $manifestBytes 字节，超过上限 ${limits.maxManifestBytes}",
            )
            return CodepackValidationResult(manifest = null, issues = issues)
        }

        val root: JsonObject = try {
            val element = ContentJson.parseToJsonElement(manifestJson)
            element as? JsonObject
                ?: return CodepackValidationResult(
                    manifest = null,
                    issues = listOf(error("manifest.json", "manifest.invalid_json", "清单必须是 JSON 对象")),
                )
        } catch (e: Exception) {
            return CodepackValidationResult(
                manifest = null,
                issues = listOf(error("manifest.json", "manifest.invalid_json", "清单 JSON 不可解析：${e.message}")),
            )
        }

        // 未知执行能力字段：P0 只接受声明式内容，出现即拒绝（不忽略字段继续安装）。
        val unknownCapabilities = root.keys.filter { it.lowercase() in FORBIDDEN_CAPABILITY_KEYS }
        for (key in unknownCapabilities) {
            issues += error(
                "manifest.json",
                "manifest.unknown_capability",
                "P0 不允许执行能力字段：$key",
            )
        }

        val manifest: PackManifest = try {
            ContentJson.decodeFromString(PackManifest.serializer(), manifestJson)
        } catch (e: MissingFieldException) {
            for (field in e.missingFields) {
                issues += error("manifest.json", "manifest.required_field", "缺少必填字段：$field")
            }
            return CodepackValidationResult(manifest = null, issues = issues)
        } catch (e: SerializationException) {
            issues += error("manifest.json", "manifest.invalid_field", "清单字段类型不合法：${e.message}")
            return CodepackValidationResult(manifest = null, issues = issues)
        }

        issues += validateManifestFields(manifest)
        return CodepackValidationResult(manifest = manifest, issues = issues)
    }

    override fun validateEntries(
        manifest: PackManifest,
        entries: List<ZipEntryInfo>,
        limits: ValidationLimits,
    ): CodepackValidationResult = validateEntries(manifest, entries, emptyMap(), limits)

    /**
     * 完整性校验重载：`actualHashes` 为解压后实测的 SHA-256（path -> 小写 hex；无法计算时为 null）。
     * 传空 Map 表示调用方尚未计算哈希，此时只校验路径集合与大小，不校验哈希（清单解析阶段已拒绝占位值）。
     */
    fun validateEntries(
        manifest: PackManifest,
        entries: List<ZipEntryInfo>,
        actualHashes: Map<String, String?>,
        limits: ValidationLimits = ValidationLimits(),
    ): CodepackValidationResult {
        val issues = mutableListOf<ValidationIssue>()

        if (entries.size > limits.maxEntries) {
            issues += error(
                "manifest.json",
                "entry.too_many",
                "条目数 ${entries.size} 超过上限 ${limits.maxEntries}",
            )
        }

        var totalBytes = 0L
        var hasManifestEntry = false
        var signaturePresent = false
        val normalizedPaths = LinkedHashSet<String>()
        val actualFiles = LinkedHashMap<String, ZipEntryInfo>()

        for (entry in entries) {
            val rawPath = entry.path
            when (val check = SafePath.checkRelative(rawPath)) {
                is SafePath.Check.Rejected -> {
                    issues += error(rawPath, pathIssueCode(check.reason), "非法条目路径：${check.reason}")
                    continue
                }

                is SafePath.Check.Ok -> {
                    val normalized = check.normalized
                    if (entry.isSymbolicLink) {
                        issues += error(normalized, "entry.symlink", "不允许符号链接条目")
                        continue
                    }
                    if (!normalizedPaths.add(normalized)) {
                        issues += error(normalized, "entry.duplicate_path", "重复条目路径")
                    }
                    val forbiddenProbe = if (entry.isDirectory) "$normalized/" else normalized
                    if (SafePath.isForbiddenEntry(forbiddenProbe)) {
                        issues += error(normalized, "entry.forbidden_type", "不允许可执行内容：$normalized")
                    }
                    if (entry.isDirectory) continue

                    if (entry.sizeBytes < 0) {
                        issues += error(normalized, "entry.invalid_size", "条目大小不能为负：${entry.sizeBytes}")
                        continue
                    }
                    if (entry.sizeBytes > limits.maxFileBytes) {
                        issues += error(
                            normalized,
                            "entry.too_large",
                            "单文件 ${entry.sizeBytes} 字节，超过上限 ${limits.maxFileBytes}",
                        )
                    }
                    totalBytes += entry.sizeBytes
                    if (entry.compressedSizeBytes > 0) {
                        val ratio = entry.sizeBytes / entry.compressedSizeBytes
                        if (ratio > limits.maxCompressionRatio) {
                            issues += error(
                                normalized,
                                "archive.compression_ratio",
                                "压缩比 $ratio 超过上限 ${limits.maxCompressionRatio}",
                            )
                        }
                    }

                    when {
                        normalized == "manifest.json" -> {
                            hasManifestEntry = true
                            if (entry.sizeBytes > limits.maxManifestBytes) {
                                issues += error(
                                    normalized,
                                    "manifest.too_large",
                                    "清单 ${entry.sizeBytes} 字节，超过上限 ${limits.maxManifestBytes}",
                                )
                            }
                        }

                        normalized.startsWith("signatures/") -> {
                            signaturePresent = true
                            actualFiles[normalized] = entry
                        }

                        else -> actualFiles[normalized] = entry
                    }
                }
            }
        }

        if (totalBytes > limits.maxTotalBytes) {
            issues += error(
                "manifest.json",
                "archive.too_large",
                "解压总量 $totalBytes 字节，超过上限 ${limits.maxTotalBytes}",
            )
        }
        if (!hasManifestEntry) {
            issues += error("manifest.json", "manifest.missing", "压缩包缺少 manifest.json")
        }
        if (signaturePresent) {
            issues += warning(
                "signatures/",
                "signature.present_but_ignored",
                "P0 不校验签名，签名目录不参与信任决策",
            )
        }

        issues += validateFileIntegrity(manifest, actualFiles, actualHashes)
        return CodepackValidationResult(manifest = manifest, issues = issues)
    }

    override fun validateCompatibility(
        manifest: PackManifest,
        appVersion: String,
    ): CodepackValidationResult {
        val issues = mutableListOf<ValidationIssue>()

        if (manifest.schemaVersion > PackManifest.SCHEMA_VERSION) {
            issues += error(
                "manifest.json",
                "compat.schema_version",
                "schemaVersion ${manifest.schemaVersion} 高于应用支持的 ${PackManifest.SCHEMA_VERSION}，请升级应用",
            )
        } else if (manifest.schemaVersion < 1) {
            issues += error("manifest.json", "manifest.invalid_schema_version", "schemaVersion 必须 ≥ 1")
        }

        when {
            manifest.pluginApiVersion > PackManifest.PLUGIN_API_VERSION -> issues += error(
                "manifest.json",
                "compat.plugin_api",
                "pluginApiVersion ${manifest.pluginApiVersion} 高于应用支持的 ${PackManifest.PLUGIN_API_VERSION}",
            )

            manifest.pluginApiVersion < 1 -> issues += error(
                "manifest.json",
                "manifest.invalid_plugin_api_version",
                "pluginApiVersion 必须 ≥ 1",
            )
        }

        val minAppVersion = SemVer.parse(manifest.minAppVersion)
        if (minAppVersion == null) {
            issues += error(
                "manifest.json",
                "manifest.invalid_min_app_version",
                "minAppVersion 不是合法 SemVer：${manifest.minAppVersion}",
            )
        } else {
            val current = SemVer.parse(appVersion)
            if (current == null) {
                issues += error(
                    "manifest.json",
                    "compat.invalid_app_version",
                    "调用方提供的 appVersion 不是合法 SemVer：$appVersion",
                )
            } else if (minAppVersion > current) {
                issues += error(
                    "manifest.json",
                    "compat.min_app_version",
                    "需要应用版本 ≥ $minAppVersion，当前 $current",
                )
            }
        }

        if (manifest.packType !in ALLOWED_PACK_TYPES) {
            issues += error("manifest.json", "manifest.invalid_pack_type", "未知 packType：${manifest.packType}")
        } else if (manifest.packType == "plugin") {
            issues += validateContributions(manifest)
        }

        return CodepackValidationResult(manifest = manifest, issues = issues)
    }

    // ------------------------------------------------------------------ 清单字段

    private fun validateManifestFields(manifest: PackManifest): List<ValidationIssue> {
        val issues = mutableListOf<ValidationIssue>()

        if (!isValidPackId(manifest.packId)) {
            issues += error(
                "manifest.json",
                "manifest.invalid_pack_id",
                "packId 必须为 1~120 位小写字母/数字/点/连字符且以字母数字开头：${manifest.packId}",
            )
        }
        if (SemVer.parse(manifest.version) == null) {
            issues += error("manifest.json", "manifest.invalid_version", "version 不是合法 SemVer：${manifest.version}")
        }
        if (manifest.packType !in ALLOWED_PACK_TYPES) {
            issues += error(
                "manifest.json",
                "manifest.invalid_pack_type",
                "packType 只能是 knowledge 或 plugin：${manifest.packType}",
            )
        }
        if (manifest.schemaVersion < 1) {
            issues += error("manifest.json", "manifest.invalid_schema_version", "schemaVersion 必须 ≥ 1")
        }
        if (manifest.pluginApiVersion < 1) {
            issues += error("manifest.json", "manifest.invalid_plugin_api_version", "pluginApiVersion 必须 ≥ 1")
        }
        if (manifest.title.isEmpty) {
            issues += error("manifest.json", "manifest.missing_title", "title 至少需要一个非空语言")
        }
        issues += validateLocales(manifest)
        if (SemVer.parse(manifest.minAppVersion) == null) {
            issues += error(
                "manifest.json",
                "manifest.invalid_min_app_version",
                "minAppVersion 不是合法 SemVer：${manifest.minAppVersion}",
            )
        }

        when (val entryCheck = SafePath.checkRelative(manifest.entry)) {
            is SafePath.Check.Rejected -> issues += error(
                manifest.entry,
                pathIssueCode(entryCheck.reason),
                "entry 路径不安全：${entryCheck.reason}",
            )

            is SafePath.Check.Ok -> Unit
        }

        if (manifest.permissions.isNotEmpty()) {
            issues += error(
                "manifest.json",
                "manifest.unknown_capability",
                "P0 不允许权限声明：${manifest.permissions.joinToString(",")}",
            )
        }

        issues += validateFiles(manifest)

        if (manifest.packType == "plugin") {
            issues += validateContributions(manifest)
        }
        return issues
    }

    private fun validateLocales(manifest: PackManifest): List<ValidationIssue> {
        val issues = mutableListOf<ValidationIssue>()
        if (manifest.defaultLocale.isBlank()) {
            issues += error("manifest.json", "manifest.invalid_locales", "defaultLocale 不能为空")
        }
        if (manifest.locales.isEmpty()) {
            issues += error("manifest.json", "manifest.invalid_locales", "locales 不能为空")
            return issues
        }
        if (manifest.locales.any { it.isBlank() }) {
            issues += error("manifest.json", "manifest.invalid_locales", "locales 不能包含空语言")
        }
        if (manifest.defaultLocale.isNotBlank() && manifest.defaultLocale !in manifest.locales) {
            issues += error(
                "manifest.json",
                "manifest.invalid_locales",
                "locales 必须包含 defaultLocale（${manifest.defaultLocale}）",
            )
        }
        val duplicates = manifest.locales.groupingBy { it }.eachCount().filterValues { it > 1 }.keys
        if (duplicates.isNotEmpty()) {
            issues += warning("manifest.json", "manifest.duplicate_locale", "locales 重复：${duplicates.joinToString(",")}")
        }
        return issues
    }

    private fun validateFiles(manifest: PackManifest): List<ValidationIssue> {
        val issues = mutableListOf<ValidationIssue>()
        val seen = HashSet<String>()
        for (file in manifest.files) {
            val normalized = when (val check = SafePath.checkRelative(file.path)) {
                is SafePath.Check.Rejected -> {
                    issues += error(
                        file.path,
                        pathIssueCode(check.reason),
                        "清单文件路径不安全：${check.reason}",
                    )
                    null
                }

                is SafePath.Check.Ok -> check.normalized
            }
            if (normalized != null) {
                if (!seen.add(normalized)) {
                    issues += error(normalized, "file.duplicate_path", "清单文件路径重复")
                }
                if (SafePath.isForbiddenEntry(normalized)) {
                    issues += error(normalized, "entry.forbidden_type", "清单声明了不允许的可执行内容")
                }
            }
            if (file.sizeBytes < 0) {
                issues += error(file.path, "file.invalid_size", "sizeBytes 不能为负：${file.sizeBytes}")
            }
            when {
                isPlaceholderHash(file.sha256) -> issues += error(
                    file.path,
                    "file.placeholder_hash",
                    "不允许占位哈希，必须由打包工具写入真实 SHA-256",
                )

                !SHA256_HEX.matches(file.sha256) -> issues += error(
                    file.path,
                    "file.invalid_hash",
                    "sha256 必须为 64 位小写 hex",
                )
            }
        }
        return issues
    }

    private fun validateContributions(manifest: PackManifest): List<ValidationIssue> {
        val issues = mutableListOf<ValidationIssue>()
        if (manifest.contributions.isEmpty()) {
            issues += error("manifest.json", "contrib.missing", "plugin 类型必须声明至少一个 contributions")
            return issues
        }
        for (contribution in manifest.contributions) {
            val type = contribution.type
            when {
                type in PluginContribution.REJECTED_TYPES -> issues += error(
                    contribution.path,
                    "contrib.rejected_type",
                    "P0 不允许贡献类型：$type",
                )

                type !in PluginContribution.ALLOWED_TYPES -> issues += error(
                    contribution.path,
                    "contrib.type_not_allowed",
                    "未知贡献类型：$type",
                )

                else -> Unit
            }
            when (val check = SafePath.checkRelative(contribution.path)) {
                is SafePath.Check.Rejected -> issues += error(
                    contribution.path,
                    "contrib.invalid_path",
                    "贡献路径不安全：${check.reason}",
                )

                is SafePath.Check.Ok -> if (SafePath.isForbiddenEntry(check.normalized)) {
                    issues += error(contribution.path, "contrib.invalid_path", "贡献路径不允许可执行内容")
                }
            }
        }
        return issues
    }

    // ------------------------------------------------------------------ 完整性

    private fun validateFileIntegrity(
        manifest: PackManifest,
        actualFiles: Map<String, ZipEntryInfo>,
        actualHashes: Map<String, String?>,
    ): List<ValidationIssue> {
        val issues = mutableListOf<ValidationIssue>()
        if (manifest.files.isEmpty()) {
            issues += error(
                "manifest.json",
                "file.missing",
                "分发产物 manifest.files 不能为空（源包请使用打包工具注入文件清单）",
            )
            return issues
        }

        val declared = LinkedHashMap<String, PackFile>()
        for (file in manifest.files) {
            val check = SafePath.checkRelative(file.path)
            if (check is SafePath.Check.Ok) {
                declared[check.normalized] = file
            }
        }

        for ((path, entry) in actualFiles) {
            if (path in declared) continue
            if (path.startsWith("signatures/")) continue // P0 忽略签名目录，只发 WARNING（见 validateEntries）
            issues += error(path, "file.unlisted", "内容文件未在 manifest.files 中声明")
        }

        for ((path, file) in declared) {
            val entry = actualFiles[path] ?: actualFiles[file.path]
            if (entry == null) {
                issues += error(path, "file.missing", "manifest.files 声明的文件不存在")
                continue
            }
            if (entry.sizeBytes != file.sizeBytes) {
                issues += error(
                    path,
                    "file.size_mismatch",
                    "声明 ${file.sizeBytes} 字节，实际 ${entry.sizeBytes} 字节",
                )
            }
            when {
                isPlaceholderHash(file.sha256) -> issues += error(
                    path,
                    "file.placeholder_hash",
                    "清单使用了占位哈希",
                )

                !SHA256_HEX.matches(file.sha256) -> issues += error(
                    path,
                    "file.invalid_hash",
                    "清单 sha256 不是 64 位小写 hex",
                )

                actualHashes.isNotEmpty() -> {
                    val actual = actualHashes[path] ?: actualHashes[file.path]
                    when {
                        actual.isNullOrBlank() -> issues += error(
                            path,
                            "file.checksum_missing",
                            "调用方未提供该文件的实测 SHA-256",
                        )

                        actual.lowercase() != file.sha256 -> issues += error(
                            path,
                            "file.sha256_mismatch",
                            "SHA-256 不一致（清单 ${file.sha256}，实测 ${actual.lowercase()}）",
                        )
                    }
                }
            }
        }
        return issues
    }

    private companion object {
        val ALLOWED_PACK_TYPES = setOf("knowledge", "plugin")
        val SHA256_HEX = Regex("^[a-f0-9]{64}$")

        /** 占位哈希（大小写变体与常见示例值）一律拒绝。 */
        val PLACEHOLDER_HASHES = setOf(
            "LOWERCASE_SHA256_HEX",
            "lowercase_sha256_hex",
            "SHA256_HEX",
            "sha256_hex",
            "PLACEHOLDER",
            "placeholder",
        )

        /**
         * 未知执行能力字段：清单解析会容忍未知字段以兼容旧版本，但这些字段代表可执行能力，
         * 必须在安全层显式拒绝，不能静默忽略。
         */
        val FORBIDDEN_CAPABILITY_KEYS = setOf(
            "runtime",
            "entrypoint",
            "entrypoints",
            "script",
            "scripts",
            "native",
            "executable",
            "exec",
            "execute",
            "binary",
            "binaries",
            "hook",
            "hooks",
            "preinstall",
            "postinstall",
            "install",
            "process",
            "spawn",
            "shell",
            "command",
            "commands",
            "main",
        )

        fun isValidPackId(raw: String): Boolean =
            raw.length in 1..120 && Regex("^[a-z0-9][a-z0-9.-]{0,119}$").matches(raw)

        fun isPlaceholderHash(raw: String): Boolean = raw in PLACEHOLDER_HASHES

        fun pathIssueCode(reason: String): String = when (reason) {
            "absolute path", "drive path" -> "entry.absolute_path"
            "parent traversal" -> "entry.parent_traversal"
            else -> "entry.invalid_path"
        }

        fun error(path: String, code: String, message: String): ValidationIssue =
            ValidationIssue(path, code, ValidationSeverity.ERROR, message)

        fun warning(path: String, code: String, message: String): ValidationIssue =
            ValidationIssue(path, code, ValidationSeverity.WARNING, message)
    }
}

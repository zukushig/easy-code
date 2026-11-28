package com.codetree.plugin

/** 校验问题严重级别：ERROR 阻断安装；WARNING 记录但不阻断。 */
enum class ValidationSeverity { ERROR, WARNING }

data class ValidationIssue(
    val path: String,
    val code: String,
    val severity: ValidationSeverity,
    val message: String,
)

data class CodepackValidationResult(
    val manifest: PackManifest? = null,
    val issues: List<ValidationIssue> = emptyList(),
) {
    val errors: List<ValidationIssue> get() = issues.filter { it.severity == ValidationSeverity.ERROR }
    val warnings: List<ValidationIssue> get() = issues.filter { it.severity == ValidationSeverity.WARNING }
    val ok: Boolean get() = errors.isEmpty()
}

/** ZIP 条目元信息：解压前先做路径与体积校验，避免 zip 炸弹与路径穿越。 */
data class ZipEntryInfo(
    val path: String,
    val sizeBytes: Long,
    val compressedSizeBytes: Long = 0L,
    val isDirectory: Boolean = false,
    val isSymbolicLink: Boolean = false,
)

/** 安装校验上限（docs/05 §11 / docs/02 §8）：可配置但必须有默认值。 */
data class ValidationLimits(
    val maxEntries: Int = 2000,
    val maxFileBytes: Long = 8L * 1024 * 1024,
    val maxTotalBytes: Long = 64L * 1024 * 1024,
    val maxCompressionRatio: Long = 100L,
    val maxManifestBytes: Int = 512 * 1024,
)

/** 清单校验器：纯函数、无 Android 依赖，便于 JVM 单测与恶意样例回归。 */
interface CodepackValidator {
    fun parseAndValidateManifest(
        manifestJson: String,
        limits: ValidationLimits = ValidationLimits(),
    ): CodepackValidationResult

    fun validateEntries(
        manifest: PackManifest,
        entries: List<ZipEntryInfo>,
        limits: ValidationLimits = ValidationLimits(),
    ): CodepackValidationResult

    fun validateCompatibility(manifest: PackManifest, appVersion: String = PackManifest.APP_VERSION): CodepackValidationResult
}

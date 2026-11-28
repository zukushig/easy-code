package com.codetree.data.platform

import com.codetree.domain.common.AppError
import com.codetree.plugin.ValidationIssue
import java.io.File
import java.net.ConnectException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.security.MessageDigest

/** SHA-256 小写 hex（清单/目录校验统一格式）。 */
internal fun ByteArray.sha256Hex(): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(this)
    return digest.joinToString("") { byte -> "%02x".format(byte) }
}

internal fun File.sha256Hex(): String = inputStream().use { input ->
    val digest = MessageDigest.getInstance("SHA-256")
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    while (true) {
        val read = input.read(buffer)
        if (read <= 0) break
        digest.update(buffer, 0, read)
    }
    digest.digest().joinToString("") { byte -> "%02x".format(byte) }
}

/** 目录递归大小（存储管理展示）。 */
internal fun File.totalBytesRecursive(): Long {
    if (!exists()) return 0L
    if (isFile) return length()
    return walkTopDown().filter { it.isFile }.sumOf { it.length() }
}

internal fun File.deleteRecursivelyQuietly() {
    runCatching { if (exists()) deleteRecursively() }
}

internal fun File.ensureDirectory(): File {
    if (!isDirectory) mkdirs()
    return this
}

/** 网络/IO 异常 → 统一 AppError（不把异常栈暴露给用户，保留 cause 供诊断）。 */
internal fun Throwable.toAppError(what: String = "操作"): AppError = when (this) {
    is SocketTimeoutException -> AppError.Timeout(what, this)
    is UnknownHostException -> AppError.NoNetwork(this)
    is ConnectException -> AppError.NoNetwork(this)
    is SocketException -> AppError.NoNetwork(this)
    is java.io.InterruptedIOException -> AppError.Timeout(what, this)
    else -> AppError.Internal("$what 失败：${message ?: this::class.simpleName.orEmpty()}", this)
}

/** 校验问题 → 用户可读错误（按稳定 code 前缀映射，保留原始 message 供日志）。 */
internal fun List<ValidationIssue>.toAppError(fallbackWhat: String = "内容包"): AppError {
    val first = firstOrNull { it.severity == com.codetree.plugin.ValidationSeverity.ERROR }
    if (first == null) {
        return AppError.Internal("$fallbackWhat 校验未通过", null)
    }
    val suffix = "（${first.code}：${first.message}）"
    return when (first.code.substringBefore('.')) {
        "compat" -> AppError.Incompatible(fallbackWhat, suffix)
        "file" -> AppError.ChecksumFailed(first.path, first.message)
        "dependency" -> AppError.DependencyMissing(first.path.ifBlank { "未知" })
        "entry", "archive" -> AppError.ResourceLimit(first.path.ifBlank { fallbackWhat })
        else -> AppError.Internal("$fallbackWhat 校验失败$suffix")
    }
}

/** 为诊断日志生成简短摘要，不包含密钥/请求体。 */
internal fun AppError.diagnosticSummary(): String = "$code: $message"

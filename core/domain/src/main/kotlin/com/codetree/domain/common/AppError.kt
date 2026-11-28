package com.codetree.domain.common

/**
 * 统一错误模型（docs/02 §5）：所有可能失败的异步操作返回 [AppResult]，禁止裸 throw。
 * code 稳定供日志/测试断言；message 面向开发者；userHint 面向用户且必须有下一步。
 */
sealed class AppError(
    val code: String,
    val message: String,
    val userHint: String,
    val cause: Throwable? = null,
) {
    class NotConfigured(what: String = "AI 服务", hint: String = "请先在「我的 → AI 设置」中填写 Base URL 与模型") :
        AppError("not_configured", "$what 未配置", hint)

    class NoNetwork(cause: Throwable? = null) :
        AppError("no_network", "网络不可用", "请检查网络连接后重试；已下载内容仍可离线使用", cause)

    class Timeout(what: String = "请求", cause: Throwable? = null) :
        AppError("timeout", "$what 超时", "稍后重试，或检查网络质量", cause)

    class Unauthorized(hint: String = "请检查密钥是否正确、是否已过期") :
        AppError("unauthorized", "未授权", hint)

    class RateLimited(val retryAfterSeconds: Long? = null) :
        AppError("rate_limited", "请求过于频繁", "请等待${retryAfterSeconds?.let { " $it 秒" } ?: "片刻"}后重试")

    class Incompatible(what: String = "内容包", detail: String = "") :
        AppError("incompatible", "$what 与当前应用版本不兼容$detail", "请升级应用或改用兼容版本内容包")

    class ChecksumFailed(path: String, detail: String = "SHA-256 不一致") :
        AppError("checksum_failed", "校验失败：$path（$detail）", "内容可能损坏，请重新下载")

    class DependencyMissing(val packId: String) :
        AppError("dependency_missing", "缺少依赖：$packId", "请先安装依赖内容包 $packId")

    class PermissionDenied(what: String = "操作") :
        AppError("permission_denied", "$what 被拒绝", "请在系统设置中授予必要权限，或改用系统文件选择器")

    class ResourceLimit(what: String = "资源") :
        AppError("resource_limit", "$what 超出上限", "请缩短输入或分批执行")

    class Cancelled : AppError("cancelled", "已取消", "已停止当前操作")

    class Internal(detail: String, cause: Throwable? = null) :
        AppError("internal", "内部错误：$detail", "请重试；若持续出现可在「我的 → 诊断」中导出日志", cause)
}

/** 统一结果类型：成功携带值，失败携带 [AppError]。 */
sealed interface AppResult<out T> {
    data class Success<out T>(val value: T) : AppResult<T>
    data class Failure(val error: AppError) : AppResult<Nothing>

    val isSuccess: Boolean get() = this is Success

    fun getOrNull(): T? = (this as? Success)?.value
    fun errorOrNull(): AppError? = (this as? Failure)?.error
}

fun <T> AppResult<T>.onSuccess(action: (T) -> Unit): AppResult<T> = apply {
    if (this is AppResult.Success) action(value)
}

fun <T> AppResult<T>.onFailure(action: (AppError) -> Unit): AppResult<T> = apply {
    if (this is AppResult.Failure) action(error)
}

inline fun <T, R> AppResult<T>.map(transform: (T) -> R): AppResult<R> = when (this) {
    is AppResult.Success -> AppResult.Success(transform(value))
    is AppResult.Failure -> this
}

inline fun <T, R> AppResult<T>.flatMap(transform: (T) -> AppResult<R>): AppResult<R> = when (this) {
    is AppResult.Success -> transform(value)
    is AppResult.Failure -> this
}

/** 包裹可能抛异常的代码，统一转为 Internal 错误；业务代码不应直接使用 try/catch 吞错。 */
inline fun <T> appRunCatching(block: () -> T): AppResult<T> = try {
    AppResult.Success(block())
} catch (cancelled: kotlinx.coroutines.CancellationException) {
    throw cancelled
} catch (t: Throwable) {
    AppResult.Failure(AppError.Internal(t.message ?: t::class.simpleName.orEmpty(), t))
}

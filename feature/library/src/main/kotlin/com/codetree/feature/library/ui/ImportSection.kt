package com.codetree.feature.library.ui

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.codetree.designsystem.CTButton
import com.codetree.designsystem.CTCard
import com.codetree.designsystem.CTDivider
import com.codetree.designsystem.CTSectionHeader
import com.codetree.designsystem.CTTag
import com.codetree.designsystem.CTTagTone
import com.codetree.designsystem.CTTextButton
import com.codetree.designsystem.CodeTreeTheme
import com.codetree.feature.library.model.ImportResultUi
import com.codetree.feature.library.model.LibraryUiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

/**
 * 导入 `.codepack`：SAF `OpenDocument` 选择文件 → 读 bytes（上限 64MiB，在 UI 侧先判断）
 * → `LibraryRepository.importCodepack(options)`；不申请全盘存储权限。
 */
@Composable
internal fun ImportSection(
    state: LibraryUiState,
    onImportBytes: (ByteArray, String) -> Unit,
    onReadFailed: (fileName: String, message: String, hint: String) -> Unit,
    onDismissResult: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val colors = CodeTreeTheme.colors
    val spacing = CodeTreeTheme.spacing
    val typography = CodeTreeTheme.typography

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            when (val read = readCodepack(context, uri)) {
                is CodepackRead.Success -> onImportBytes(read.bytes, read.fileName)
                is CodepackRead.Failure -> onReadFailed(read.fileName, read.message, read.hint)
            }
        }
    }

    CTSectionHeader(
        title = "导入插件 / 内容包",
        subtitle = "支持 .codepack（声明式内容，无可执行代码）",
    )
    CTCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = spacing.pageHorizontal),
    ) {
        Text(
            text = "从系统文件选择器选择 .codepack 文件。应用只读取你选中的那个文件，不申请全盘存储权限。",
            style = typography.callout,
            color = colors.textSecondary,
        )
        Text(
            text = "导入前会校验清单、版本兼容、依赖与文件哈希；失败会显示原因与下一步，不会留下半安装状态。",
            style = typography.caption,
            color = colors.textTertiary,
            modifier = Modifier.padding(top = spacing.xxs),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.End,
        ) {
            CTButton(
                text = if (state.importing) "导入中…" else "选择文件",
                onClick = {
                    launcher.launch(arrayOf("application/zip", "application/octet-stream", "*/*"))
                },
                enabled = !state.importing,
            )
        }
    }
    val result = state.importResult
    if (result != null) {
        ImportResultCard(result = result, onDismiss = onDismissResult)
        Spacer(Modifier.height(spacing.sm))
    }
    CTDivider(modifier = Modifier.padding(horizontal = spacing.pageHorizontal))
}

@Composable
private fun ImportResultCard(result: ImportResultUi, onDismiss: () -> Unit) {
    val colors = CodeTreeTheme.colors
    val spacing = CodeTreeTheme.spacing
    val typography = CodeTreeTheme.typography
    CTCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = spacing.pageHorizontal)
            .padding(top = spacing.sm),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = if (result.success) "导入成功" else "导入失败",
                style = typography.headline,
                color = if (result.success) colors.success else colors.error,
                modifier = Modifier.weight(1f),
            )
            CTTag(
                text = if (result.success) "已安装" else result.errorCode ?: "失败",
                tone = if (result.success) CTTagTone.SUCCESS else CTTagTone.DANGER,
            )
        }
        if (result.fileName.isNotBlank()) {
            Text(
                text = "文件：${result.fileName}",
                style = typography.caption,
                color = colors.textTertiary,
                modifier = Modifier.padding(top = spacing.xxs),
            )
        }
        if (result.success) {
            Text(
                text = "${result.title}（${result.packId}）",
                style = typography.callout,
                color = colors.textPrimary,
                modifier = Modifier.padding(top = spacing.xs),
            )
            Text(
                text = "版本 v${result.version} · 来源 ${result.sourceLabel} · ${result.message}",
                style = typography.footnote,
                color = colors.textSecondary,
            )
        } else {
            Text(
                text = result.message,
                style = typography.callout,
                color = colors.textPrimary,
                modifier = Modifier.padding(top = spacing.xs),
            )
            if (!result.errorHint.isNullOrBlank()) {
                Text(
                    text = result.errorHint,
                    style = typography.footnote,
                    color = colors.warning,
                    modifier = Modifier.padding(top = spacing.xxs),
                )
            }
            Text(
                text = "详细校验明细（path + code）受当前契约限制暂未展示，可重试或换一个内容包。",
                style = typography.caption,
                color = colors.textTertiary,
                modifier = Modifier.padding(top = spacing.xxs),
            )
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            CTTextButton(text = "关闭", onClick = onDismiss, color = colors.textSecondary)
        }
    }
    Spacer(Modifier.height(spacing.xs))
}

private sealed interface CodepackRead {
    data class Success(val bytes: ByteArray, val fileName: String) : CodepackRead
    data class Failure(val fileName: String, val message: String, val hint: String) : CodepackRead
}

private suspend fun readCodepack(context: Context, uri: Uri): CodepackRead = withContext(Dispatchers.IO) {
    val fileName = queryDisplayName(context, uri) ?: "imported.codepack"
    try {
        var tooLarge = false
        var bytes: ByteArray? = null
        context.contentResolver.openInputStream(uri)?.use { input ->
            val output = ByteArrayOutputStream()
            val chunk = ByteArray(64 * 1024)
            var total = 0L
            while (true) {
                val read = input.read(chunk)
                if (read <= 0) break
                total += read
                if (total > MAX_IMPORT_BYTES) {
                    tooLarge = true
                    break
                }
                output.write(chunk, 0, read)
            }
            if (!tooLarge) bytes = output.toByteArray()
        }
        when {
            tooLarge -> CodepackRead.Failure(
                fileName = fileName,
                message = "文件超过 64 MiB 上限，已拒绝读取",
                hint = "P0 校验上限为 64 MiB；请选择更小的内容包或重新导出",
            )
            bytes == null || bytes!!.isEmpty() -> CodepackRead.Failure(
                fileName = fileName,
                message = "无法读取所选文件",
                hint = "请重新选择文件，确认文件未损坏或未被移除",
            )
            else -> CodepackRead.Success(bytes = bytes!!, fileName = fileName)
        }
    } catch (t: Throwable) {
        CodepackRead.Failure(
            fileName = fileName,
            message = "读取文件失败：${t.message ?: t::class.simpleName ?: "未知错误"}",
            hint = "请重新选择文件；不需要存储权限，走系统文件选择器即可",
        )
    }
}

private fun queryDisplayName(context: Context, uri: Uri): String? = runCatching {
    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst() && cursor.columnCount > 0) cursor.getString(0) else null
    }
}.getOrNull()

private const val MAX_IMPORT_BYTES = 64L * 1024 * 1024

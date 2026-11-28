package com.codetree.feature.library.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.codetree.designsystem.CTButton
import com.codetree.designsystem.CTButtonStyle
import com.codetree.designsystem.CTCard
import com.codetree.designsystem.CTEmptyState
import com.codetree.designsystem.CTIcons
import com.codetree.designsystem.CTSectionHeader
import com.codetree.designsystem.CTTag
import com.codetree.designsystem.CTTagTone
import com.codetree.designsystem.CTTextButton
import com.codetree.designsystem.CodeTreeTheme
import com.codetree.domain.model.PackCatalogEntry
import com.codetree.feature.library.model.CatalogItemUi
import com.codetree.feature.library.model.LibraryUiState
import com.codetree.feature.library.model.PackStatus
import com.codetree.feature.library.util.formatBytes
import com.codetree.feature.library.util.formatProgress

/** 知识库目录：按领域/难度展示，支持下载 / 更新 / 取消下载（MVP 无暂停恢复）。 */
@Composable
internal fun CatalogSection(
    state: LibraryUiState,
    onDownload: (PackCatalogEntry) -> Unit,
    onCancelDownload: () -> Unit,
) {
    val spacing = CodeTreeTheme.spacing
    CTSectionHeader(
        title = "知识库目录",
        subtitle = "下载后可离线使用；大小与依赖在下载前展示",
    )
    if (state.catalog.isEmpty()) {
        CTEmptyState(
            title = "暂无可下载内容",
            description = "内容目录为空。内置内容包可能已在「已安装」中，或需要更新内容源。",
            icon = CTIcons.Book,
        )
        return
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = spacing.pageHorizontal),
        verticalArrangement = Arrangement.spacedBy(spacing.sm),
    ) {
        state.catalog.forEach { item ->
            CatalogItemCard(
                item = item,
                busy = state.busy,
                onDownload = { onDownload(item.entry) },
                onCancelDownload = onCancelDownload,
            )
        }
    }
}

@Composable
private fun CatalogItemCard(
    item: CatalogItemUi,
    busy: Boolean,
    onDownload: () -> Unit,
    onCancelDownload: () -> Unit,
) {
    val colors = CodeTreeTheme.colors
    val spacing = CodeTreeTheme.spacing
    val typography = CodeTreeTheme.typography
    CTCard(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = item.title,
                style = typography.headline,
                color = colors.textPrimary,
                modifier = Modifier.weight(1f),
            )
            StatusTag(status = item.status)
        }
        Row(
            modifier = Modifier.padding(top = spacing.xxs),
            horizontalArrangement = Arrangement.spacedBy(spacing.xs),
        ) {
            CTTag(text = item.domainLabel, tone = CTTagTone.NEUTRAL)
            CTTag(text = item.difficultyLabel, tone = CTTagTone.NEUTRAL)
            CTTag(text = "v${item.entry.version}", tone = CTTagTone.ACCENT)
        }
        Text(
            text = "大小 ${formatBytes(item.entry.sizeBytes)} · 依赖 " +
                if (item.dependencyIds.isEmpty()) "无" else item.dependencyIds.joinToString("、"),
            style = typography.caption,
            color = colors.textSecondary,
            modifier = Modifier.padding(top = spacing.xs),
        )
        if (item.entry.locales.isNotEmpty()) {
            Text(
                text = "语言：${item.entry.locales.joinToString(" / ")}",
                style = typography.caption,
                color = colors.textTertiary,
            )
        }
        val installed = item.installed
        if (installed != null) {
            Text(
                text = "已安装 v${installed.version}" + if (item.status == PackStatus.UPDATE_AVAILABLE) "，有可用更新" else "",
                style = typography.caption,
                color = if (item.status == PackStatus.UPDATE_AVAILABLE) colors.warning else colors.success,
                modifier = Modifier.padding(top = spacing.xxs),
            )
        }
        if (item.status == PackStatus.DOWNLOADING) {
            Spacer(Modifier.width(spacing.xs))
            val percent = formatProgress(item.bytesRead, item.contentLength)
            if (item.progress != null) {
                LinearProgressIndicator(
                    progress = { item.progress.coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth(),
                    color = colors.accent,
                    trackColor = colors.separator,
                )
            } else {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    color = colors.accent,
                    trackColor = colors.separator,
                )
            }
            Text(
                text = if (percent != null) {
                    "下载中 $percent%（${formatBytes(item.bytesRead)} / ${formatBytes(item.contentLength)}）"
                } else {
                    "下载中…（${formatBytes(item.bytesRead)}）"
                },
                style = typography.caption,
                color = colors.textSecondary,
                modifier = Modifier.padding(top = spacing.xxs),
            )
        }
        if (item.errorHint != null && item.status == PackStatus.FAILED) {
            Text(
                text = "上次失败：${item.errorHint}",
                style = typography.footnote,
                color = colors.error,
                modifier = Modifier.padding(top = spacing.xs),
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = spacing.sm),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            when (item.status) {
                PackStatus.DOWNLOADING -> CTTextButton(
                    text = "取消下载",
                    onClick = onCancelDownload,
                    color = colors.error,
                )
                PackStatus.INSTALLED -> Text(
                    text = "可在「已安装」中启停或卸载",
                    style = typography.caption,
                    color = colors.textTertiary,
                )
                PackStatus.UPDATE_AVAILABLE -> CTButton(
                    text = "更新",
                    onClick = onDownload,
                    enabled = !busy,
                    style = CTButtonStyle.TINTED,
                )
                PackStatus.FAILED -> CTButton(
                    text = "重试下载",
                    onClick = onDownload,
                    enabled = !busy,
                )
                PackStatus.NOT_INSTALLED -> CTButton(
                    text = "下载",
                    onClick = onDownload,
                    enabled = !busy,
                )
            }
        }
    }
}

@Composable
private fun StatusTag(status: PackStatus) {
    val tone = when (status) {
        PackStatus.NOT_INSTALLED -> CTTagTone.NEUTRAL
        PackStatus.INSTALLED -> CTTagTone.SUCCESS
        PackStatus.UPDATE_AVAILABLE -> CTTagTone.WARNING
        PackStatus.DOWNLOADING -> CTTagTone.ACCENT
        PackStatus.FAILED -> CTTagTone.DANGER
    }
    val label = when (status) {
        PackStatus.NOT_INSTALLED -> "未安装"
        PackStatus.INSTALLED -> "已安装"
        PackStatus.UPDATE_AVAILABLE -> "有更新"
        PackStatus.DOWNLOADING -> "下载中"
        PackStatus.FAILED -> "失败"
    }
    CTTag(text = label, tone = tone)
}

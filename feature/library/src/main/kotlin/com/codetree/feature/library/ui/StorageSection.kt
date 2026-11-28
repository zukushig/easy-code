package com.codetree.feature.library.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.codetree.designsystem.CTCard
import com.codetree.designsystem.CTSectionHeader
import com.codetree.designsystem.CodeTreeTheme
import com.codetree.feature.library.model.LibraryUiState
import com.codetree.feature.library.util.formatBytes

/** 存储概览：总占用 + 目录/已安装数量（PRD PKG-001 显示已占空间）。 */
@Composable
internal fun StorageSection(state: LibraryUiState) {
    val colors = CodeTreeTheme.colors
    val spacing = CodeTreeTheme.spacing
    val typography = CodeTreeTheme.typography

    CTSectionHeader(
        title = "存储与内容",
        subtitle = "已安装内容可离线使用；删除后相关索引会一并清理",
    )
    CTCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = spacing.pageHorizontal),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = "知识库总占用", style = typography.callout, color = colors.textSecondary)
                Text(
                    text = formatBytes(state.storageUsageBytes),
                    style = typography.title2,
                    color = colors.textPrimary,
                    modifier = Modifier.padding(top = spacing.xxs),
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "目录 ${state.catalogCount} 个",
                    style = typography.footnote,
                    color = colors.textSecondary,
                )
                Text(
                    text = "已安装 ${state.installedCount} 个",
                    style = typography.footnote,
                    color = colors.textSecondary,
                    modifier = Modifier.padding(top = spacing.xxs),
                )
            }
        }
        Row(modifier = Modifier.padding(top = spacing.sm)) {
            Text(
                text = "已安装包体积合计 ${formatBytes(state.installedSizeBytes)}",
                style = typography.caption,
                color = colors.textTertiary,
            )
        }
    }
    Spacer(Modifier.width(spacing.xs))
}

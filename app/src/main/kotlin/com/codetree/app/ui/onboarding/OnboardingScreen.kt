package com.codetree.app.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.codetree.app.R
import com.codetree.app.ui.AllDirections
import com.codetree.app.ui.primaryDirectionOf
import com.codetree.designsystem.CTButton
import com.codetree.designsystem.CTTextButton
import com.codetree.designsystem.CTIcons
import com.codetree.designsystem.CTPill
import com.codetree.designsystem.CodeTreeTheme
import com.codetree.designsystem.CTScaffold
import com.codetree.designsystem.CTLoading

/**
 * 三页首次引导（docs/specs/design-system.md §6 `OnboardingPage`）：
 * ① 介绍四件事（知识树 / 词典 / 终端 / AI）→ ② 12 个学习方向多选（至少 1 个）→ ③ 完成页。
 *
 * 可跳过；完成/跳过都由调用方持久化 `onboardingDone`。多选方向里只持久化主方向
 * （`AppSettings.activeDirection` 是单值契约，见 ui/Directions.kt 注释）。
 */
@Composable
fun OnboardingScreen(
    initialDirection: String?,
    onComplete: (primaryDirection: String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var page by rememberSaveable { mutableStateOf(0) }
    var selectedCsv by rememberSaveable {
        mutableStateOf(initialDirection?.takeIf { id -> AllDirections.any { it.id == id } }.orEmpty())
    }
    val selected = selectedCsv.split(',').filter { it.isNotBlank() }.toSet()

    val colors = CodeTreeTheme.colors
    val spacing = CodeTreeTheme.spacing

    CTScaffold(modifier = modifier) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = spacing.xxl),
        ) {
            // 顶栏：返回（第 2/3 页）+ 跳过
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (page > 0) {
                    CTTextButton(
                        text = stringResource(R.string.action_back),
                        onClick = { page -= 1 },
                        color = colors.textSecondary,
                    )
                }
                Spacer(Modifier.weight(1f))
                CTTextButton(
                    text = stringResource(R.string.action_skip),
                    onClick = { onComplete(null) },
                    color = colors.textSecondary,
                )
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
            ) {
                when (page) {
                    0 -> IntroPage()
                    1 -> DirectionPage(
                        selected = selected,
                        onToggle = { id ->
                            val next = selected.toMutableSet()
                            if (!next.add(id)) next.remove(id)
                            selectedCsv = next.joinToString(",")
                        },
                    )
                    else -> FinishPage()
                }
            }

            // 页码指示
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = spacing.md),
                horizontalArrangement = Arrangement.Center,
            ) {
                repeat(3) { index ->
                    Box(
                        modifier = Modifier
                            .padding(horizontal = spacing.xxs)
                            .size(if (index == page) 8.dp else 6.dp)
                            .clip(CodeTreeTheme.shapes.pillShape)
                            .background(if (index == page) colors.accent else colors.separator),
                    )
                }
            }

            when (page) {
                0 -> CTButton(
                    text = stringResource(R.string.action_continue),
                    onClick = { page = 1 },
                    modifier = Modifier.fillMaxWidth(),
                )

                1 -> {
                    if (selected.isEmpty()) {
                        Text(
                            text = stringResource(R.string.onboarding_page2_hint),
                            style = CodeTreeTheme.typography.footnote,
                            color = colors.warning,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(bottom = spacing.sm),
                        )
                    }
                    CTButton(
                        text = stringResource(R.string.action_continue),
                        onClick = { page = 2 },
                        enabled = selected.isNotEmpty(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                else -> CTButton(
                    text = stringResource(R.string.action_start_learning),
                    onClick = { onComplete(primaryDirectionOf(selected)) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Spacer(Modifier.height(spacing.xxl))
        }
    }
}

@Composable
private fun IntroPage() {
    val spacing = CodeTreeTheme.spacing
    GradientIllustration(icon = CTIcons.Learn)
    Spacer(Modifier.height(spacing.xl))
    Text(
        text = stringResource(R.string.onboarding_page1_title),
        style = CodeTreeTheme.typography.largeTitle,
        color = CodeTreeTheme.colors.textPrimary,
    )
    Spacer(Modifier.height(spacing.md))
    Text(
        text = stringResource(R.string.onboarding_page1_body),
        style = CodeTreeTheme.typography.body,
        color = CodeTreeTheme.colors.textSecondary,
    )
    Spacer(Modifier.height(spacing.xl))
    HighlightRow(CTIcons.Graph, R.string.onboarding_highlight_tree_title, R.string.onboarding_highlight_tree_desc)
    HighlightRow(CTIcons.Dictionary, R.string.onboarding_highlight_dictionary_title, R.string.onboarding_highlight_dictionary_desc)
    HighlightRow(CTIcons.Terminal, R.string.onboarding_highlight_terminal_title, R.string.onboarding_highlight_terminal_desc)
    HighlightRow(CTIcons.Assistant, R.string.onboarding_highlight_ai_title, R.string.onboarding_highlight_ai_desc)
}

@Composable
private fun DirectionPage(selected: Set<String>, onToggle: (String) -> Unit) {
    val spacing = CodeTreeTheme.spacing
    Text(
        text = stringResource(R.string.onboarding_page2_title),
        style = CodeTreeTheme.typography.largeTitle,
        color = CodeTreeTheme.colors.textPrimary,
    )
    Spacer(Modifier.height(spacing.md))
    Text(
        text = stringResource(R.string.onboarding_page2_body),
        style = CodeTreeTheme.typography.body,
        color = CodeTreeTheme.colors.textSecondary,
    )
    Spacer(Modifier.height(spacing.xl))
    AllDirections.chunked(2).forEach { rowItems ->
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(spacing.sm),
        ) {
            rowItems.forEach { direction ->
                CTPill(
                    text = stringResource(direction.labelRes),
                    selected = direction.id in selected,
                    onClick = { onToggle(direction.id) },
                    modifier = Modifier.weight(1f),
                )
            }
            if (rowItems.size == 1) {
                Spacer(Modifier.weight(1f))
            }
        }
        Spacer(Modifier.height(spacing.sm))
    }
}

@Composable
private fun FinishPage() {
    val spacing = CodeTreeTheme.spacing
    GradientIllustration(icon = CTIcons.Check)
    Spacer(Modifier.height(spacing.xl))
    Text(
        text = stringResource(R.string.onboarding_page3_title),
        style = CodeTreeTheme.typography.largeTitle,
        color = CodeTreeTheme.colors.textPrimary,
    )
    Spacer(Modifier.height(spacing.md))
    Text(
        text = stringResource(R.string.onboarding_page3_body),
        style = CodeTreeTheme.typography.body,
        color = CodeTreeTheme.colors.textSecondary,
    )
}

@Composable
private fun HighlightRow(icon: ImageVector, titleRes: Int, descRes: Int) {
    val spacing = CodeTreeTheme.spacing
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = CodeTreeTheme.colors.accent,
            modifier = Modifier.size(24.dp),
        )
        Spacer(Modifier.width(spacing.md))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(titleRes),
                style = CodeTreeTheme.typography.headline,
                color = CodeTreeTheme.colors.textPrimary,
            )
            Text(
                text = stringResource(descRes),
                style = CodeTreeTheme.typography.footnote,
                color = CodeTreeTheme.colors.textSecondary,
            )
        }
    }
}

/** 插画式渐变图形：单一强调色的柔和渐变 + 细线图标（不依赖美术资源）。 */
@Composable
private fun GradientIllustration(icon: ImageVector) {
    val colors = CodeTreeTheme.colors
    val shapes = CodeTreeTheme.shapes
    val spacing = CodeTreeTheme.spacing
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp)
            .clip(shapes.roundedXLarge)
            .background(
                Brush.linearGradient(
                    listOf(
                        colors.accent.copy(alpha = 0.20f),
                        colors.accentSoft,
                        colors.surfaceElevated,
                    ),
                ),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = colors.accent,
            modifier = Modifier.size(76.dp),
        )
        // 右下角装饰点，保持克制
        Row(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(spacing.lg),
            horizontalArrangement = Arrangement.spacedBy(spacing.xs),
        ) {
            repeat(3) { index ->
                Box(
                    modifier = Modifier
                        .size(if (index == 2) 8.dp else 5.dp)
                        .clip(shapes.pillShape)
                        .background(colors.accent.copy(alpha = 0.35f)),
                )
            }
        }
    }
}

/** 供设置页「学习方向重选」复用（不带引导步骤，只做多选 + 保存）。 */
@Composable
fun DirectionSelector(
    selected: Set<String>,
    onToggle: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = CodeTreeTheme.spacing
    Column(modifier = modifier) {
        AllDirections.chunked(2).forEach { rowItems ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(spacing.sm),
            ) {
                rowItems.forEach { direction ->
                    CTPill(
                        text = stringResource(direction.labelRes),
                        selected = direction.id in selected,
                        onClick = { onToggle(direction.id) },
                        modifier = Modifier.weight(1f),
                    )
                }
                if (rowItems.size == 1) Spacer(Modifier.weight(1f))
            }
            Spacer(Modifier.height(spacing.sm))
        }
    }
}

/** 引导加载占位（设置读取完成前使用，避免闪现错误主题）。 */
@Composable
fun OnboardingLoading() {
    CTLoading(fullScreen = true, delayMs = 0)
}

package com.codetree.feature.learn.ui

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import com.codetree.designsystem.CTButton
import com.codetree.designsystem.CTButtonStyle
import com.codetree.designsystem.CTCard
import com.codetree.designsystem.CTDivider
import com.codetree.designsystem.CTIconButton
import com.codetree.designsystem.CTListRow
import com.codetree.designsystem.CTPill
import com.codetree.designsystem.CTSectionHeader
import com.codetree.designsystem.CTSegmentedControl
import com.codetree.designsystem.CTTag
import com.codetree.designsystem.CTTagTone
import com.codetree.designsystem.CTIcons

/** 按钮三态，映射 designsystem 的 CTButtonStyle。 */
enum class LearnButtonStyle { FILLED, TINTED, PLAIN }

/** 语义色调，映射 designsystem 的 CTTagTone。 */
enum class LearnTone { NEUTRAL, ACCENT, SUCCESS, WARNING, ERROR }

private fun LearnTone.toCtTagTone(): CTTagTone = when (this) {
    LearnTone.NEUTRAL -> CTTagTone.NEUTRAL
    LearnTone.ACCENT -> CTTagTone.ACCENT
    LearnTone.SUCCESS -> CTTagTone.SUCCESS
    LearnTone.WARNING -> CTTagTone.WARNING
    LearnTone.ERROR -> CTTagTone.DANGER
}

private fun LearnButtonStyle.toCtButtonStyle(): CTButtonStyle = when (this) {
    LearnButtonStyle.FILLED -> CTButtonStyle.FILLED
    LearnButtonStyle.TINTED -> CTButtonStyle.TINTED
    LearnButtonStyle.PLAIN -> CTButtonStyle.PLAIN
}

/** 卡片：转调 designsystem `CodeTreeCard`/CTCard（surface + large 圆角 + 16dp 内边距）。 */
@Composable
fun LearnCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    CTCard(modifier = modifier, onClick = onClick, content = content)
}

/** 三态按钮：转调 designsystem `CodeTreeButton`/CTButton。 */
@Composable
fun LearnButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    style: LearnButtonStyle = LearnButtonStyle.FILLED,
) {
    CTButton(
        text = text,
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        style = style.toCtButtonStyle(),
    )
}

/** 分段控件：转调 designsystem `SegmentedControl`/CTSegmentedControl。 */
@Composable
fun LearnSegmentedControl(
    options: List<String>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    CTSegmentedControl(options = options, selectedIndex = selectedIndex, onSelect = onSelected, modifier = modifier)
}

/** 区块标题：转调 designsystem `SectionHeader`/CTSectionHeader。 */
@Composable
fun LearnSectionHeader(title: String, modifier: Modifier = Modifier) {
    CTSectionHeader(title = title, modifier = modifier)
}

/** 小徽标：转调 designsystem `CTTag`。 */
@Composable
fun LearnBadge(text: String, tone: LearnTone = LearnTone.NEUTRAL, modifier: Modifier = Modifier) {
    CTTag(text = text, modifier = modifier, tone = tone.toCtTagTone())
}

/** 可点按标签：转调 designsystem `CTPill`。 */
@Composable
fun LearnChip(
    text: String,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    CTPill(text = text, modifier = modifier, selected = selected, onClick = onClick)
}

/** 列表行：转调 designsystem `ListRow`/CTListRow。 */
@Composable
fun LearnListRow(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    CTListRow(
        title = title,
        modifier = modifier,
        subtitle = subtitle,
        trailing = trailing,
        onClick = onClick,
    )
}

/** 分割线：转调 designsystem `CTDivider`。 */
@Composable
fun LearnDivider(modifier: Modifier = Modifier) {
    CTDivider(modifier = modifier)
}

/** 顶部返回栏（二级页面）：48dp 触控返回 + 标题。 */
@Composable
fun LearnTopBar(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    actions: (@Composable () -> Unit)? = null,
) {
    val palette = learnPalette()
    val type = learnType()
    val spacing = learnSpacing()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = spacing.s, vertical = spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CTIconButton(
            icon = CTIcons.Back,
            contentDescription = "返回",
            onClick = onBack,
            tint = palette.textPrimary,
        )
        Text(
            text = title,
            style = type.headline,
            color = palette.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        actions?.invoke()
    }
}

/** 刷新图标按钮：转调 `CTIconButton`。 */
@Composable
fun LearnRefreshButton(onClick: () -> Unit) {
    CTIconButton(
        icon = CTIcons.Refresh,
        contentDescription = "刷新",
        onClick = onClick,
        tint = learnPalette().accent,
    )
}

// 保留语义色扩展供画布等非 Compose 绘制代码使用。
fun LearnPalette.toneColor(tone: LearnTone): Color = when (tone) {
    LearnTone.NEUTRAL -> textSecondary
    LearnTone.ACCENT -> accent
    LearnTone.SUCCESS -> success
    LearnTone.WARNING -> warning
    LearnTone.ERROR -> error
}

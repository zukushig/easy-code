package com.codetree.feature.assistant.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import com.codetree.designsystem.CTDivider
import com.codetree.designsystem.CTIconButton
import com.codetree.designsystem.CTIcons
import com.codetree.designsystem.CodeTreeTheme
import com.codetree.feature.assistant.model.AssistantUiState

/** 输入区：多行输入 + 添加上下文 + 发送/停止；未配置时给出引导。 */
@Composable
internal fun Composer(
    state: AssistantUiState,
    onUpdate: (String) -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
    onAddCode: () -> Unit,
    onOpenConfig: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = CodeTreeTheme.colors
    val shapes = CodeTreeTheme.shapes
    val spacing = CodeTreeTheme.spacing
    val typography = CodeTreeTheme.typography

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.background)
            .navigationBarsPadding()
            .imePadding(),
    ) {
        CTDivider()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = spacing.sm, vertical = spacing.sm),
            verticalAlignment = Alignment.Bottom,
        ) {
            CTIconButton(
                icon = CTIcons.Plus,
                contentDescription = "添加上下文",
                onClick = onAddCode,
                tint = colors.textSecondary,
            )
            BasicTextField(
                value = state.composer,
                onValueChange = onUpdate,
                modifier = Modifier
                    .weight(1f)
                    .background(colors.surface, shapes.roundedLarge)
                    .padding(horizontal = spacing.md, vertical = spacing.sm),
                textStyle = typography.body.copy(color = colors.textPrimary),
                cursorBrush = SolidColor(colors.accent),
                maxLines = 5,
                keyboardOptions = KeyboardOptions(
                    autoCorrect = false,
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Send,
                ),
                keyboardActions = KeyboardActions(onSend = { if (state.composer.isNotBlank()) onSend() }),
                decorationBox = { innerTextField ->
                    Box {
                        if (state.composer.isEmpty()) {
                            Text(
                                text = "问点什么…例如「这段代码为什么报错」",
                                style = typography.body,
                                color = colors.textTertiary,
                            )
                        }
                        innerTextField()
                    }
                },
            )
            if (state.streaming) {
                CTIconButton(
                    icon = CTIcons.Stop,
                    contentDescription = "停止生成",
                    onClick = onStop,
                    tint = colors.error,
                )
            } else {
                CTIconButton(
                    icon = CTIcons.Send,
                    contentDescription = "发送",
                    onClick = onSend,
                    enabled = state.composer.isNotBlank(),
                    tint = colors.accent,
                )
            }
        }
        if (!state.configured) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = spacing.md)
                    .padding(bottom = spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "未配置 AI 服务：发送会被阻止",
                    style = typography.caption,
                    color = colors.warning,
                )
                Text(
                    text = "去配置",
                    style = typography.caption,
                    color = colors.accent,
                    modifier = Modifier
                        .padding(start = spacing.sm)
                        .clickable(onClick = onOpenConfig),
                )
            }
        }
    }
}

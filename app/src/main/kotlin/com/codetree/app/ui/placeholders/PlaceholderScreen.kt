package com.codetree.app.ui.placeholders

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.codetree.designsystem.CTDivider
import com.codetree.designsystem.CTLargeTitleBar
import com.codetree.designsystem.CTScaffold
import com.codetree.designsystem.CTTag
import com.codetree.designsystem.CTTagTone
import com.codetree.designsystem.CodeTreeTheme

/**
 * feature 入口尚未落盘时的临时页面（仅在 app 内使用）。
 *
 * 集成阶段替换方式：见 `ui/FeatureSurfaces.kt` 中的 `TODO(T7)` / `TODO(T8)` 注释，
 * 把对应的 Surface 换成语义一致的 feature 入口 Composable 即可，无需改导航代码。
 */
@Composable
fun PlaceholderScreen(
    title: String,
    description: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    owner: String = "T7/T8",
) {
    val colors = CodeTreeTheme.colors
    val typography = CodeTreeTheme.typography
    val spacing = CodeTreeTheme.spacing

    CTScaffold(modifier = modifier.fillMaxSize()) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            Column(modifier = Modifier.fillMaxSize()) {
                CTLargeTitleBar(title = title)
                CTDivider()
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = spacing.xxl),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = colors.textTertiary,
                        modifier = Modifier.size(44.dp),
                    )
                    Spacer(Modifier.height(spacing.lg))
                    Text(
                        text = title,
                        style = typography.title2,
                        color = colors.textPrimary,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(spacing.sm))
                    Text(
                        text = description,
                        style = typography.callout,
                        color = colors.textSecondary,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(spacing.lg))
                    CTTag(text = "TODO($owner)", tone = CTTagTone.WARNING)
                    Spacer(Modifier.height(spacing.sm))
                    Text(
                        text = "入口已接线，feature 落盘后替换占位实现。",
                        style = typography.caption,
                        color = colors.textTertiary,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

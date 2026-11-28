package com.codetree.feature.assistant.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.codetree.designsystem.CTButton
import com.codetree.designsystem.CTDivider
import com.codetree.designsystem.CTSheetHeader
import com.codetree.designsystem.CTSliderRow
import com.codetree.designsystem.CTTextButton
import com.codetree.designsystem.CodeTreeTheme
import com.codetree.feature.assistant.model.AssistantUiState
import java.util.Locale

/**
 * AI 配置页（底部弹层）：Base URL / 模型 / API Key（密文 + 清除）/ temperature / maxTokens。
 * 密钥只通过 `SettingsRepository.setApiKey` 保存，不写入会话文件与日志。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AiConfigSheet(
    state: AssistantUiState,
    onSave: (baseUrl: String, model: String, apiKey: String?, temperature: Double, maxTokens: Int?) -> Unit,
    onClearKey: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val colors = CodeTreeTheme.colors
    val spacing = CodeTreeTheme.spacing
    val typography = CodeTreeTheme.typography

    var baseUrl by remember(state.baseUrl) { mutableStateOf(state.baseUrl) }
    var model by remember(state.modelName) { mutableStateOf(state.modelName) }
    var apiKey by remember { mutableStateOf("") }
    var temperature by remember { mutableStateOf(0.3f) }
    var maxTokensText by remember { mutableStateOf("") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
        ) {
            CTSheetHeader(
                title = "AI 设置",
                subtitle = "OpenAI 兼容接口；密钥保存后仅用于请求头",
                onClose = onDismiss,
            )
            Column(modifier = Modifier.padding(horizontal = spacing.pageHorizontal)) {
                OutlinedTextField(
                    value = baseUrl,
                    onValueChange = { baseUrl = it },
                    label = { Text("Base URL") },
                    placeholder = { Text("https://api.example.com/v1") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(spacing.sm))
                OutlinedTextField(
                    value = model,
                    onValueChange = { model = it },
                    label = { Text("模型名") },
                    placeholder = { Text("例如 gpt-4o-mini") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(spacing.sm))
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    label = { Text("API Key") },
                    placeholder = {
                        Text(if (state.hasApiKey) "已保存（留空则不修改）" else "sk-…")
                    },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (state.hasApiKey) {
                    CTTextButton(text = "清除已保存密钥", onClick = onClearKey, color = colors.error)
                }
                Spacer(Modifier.height(spacing.xs))
                CTSliderRow(
                    title = "temperature",
                    value = temperature,
                    onValueChange = { temperature = it.coerceIn(0f, 2f) },
                    valueRange = 0f..2f,
                    steps = 18,
                    valueLabel = String.format(Locale.US, "%.1f", temperature),
                    subtitle = "越高越发散；学习场景建议 0.2～0.5",
                )
                OutlinedTextField(
                    value = maxTokensText,
                    onValueChange = { maxTokensText = it.filter { char -> char.isDigit() }.take(6) },
                    label = { Text("maxTokens（可选）") },
                    placeholder = { Text("留空表示服务端默认") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = "请求地址 = Base URL + /chat/completions。本地 127.0.0.1 允许 http，其余请使用 https。",
                    style = typography.footnote,
                    color = colors.textSecondary,
                    modifier = Modifier.padding(top = spacing.sm),
                )
                Text(
                    text = "MVP 未提供连接测试：保存后回到对话页发送一条消息即可验证配置；401/429/超时会显示原因与下一步。",
                    style = typography.footnote,
                    color = colors.textTertiary,
                    modifier = Modifier.padding(top = spacing.xxs),
                )
                Spacer(Modifier.height(spacing.md))
                CTButton(
                    text = "保存配置",
                    onClick = {
                        onSave(
                            baseUrl.trim(),
                            model.trim(),
                            apiKey.takeIf { it.isNotBlank() },
                            temperature.toDouble(),
                            maxTokensText.toIntOrNull(),
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(spacing.xxl))
            }
            CTDivider()
        }
    }
}

package com.codetree.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.codetree.app.R
import com.codetree.app.di.AppContainer
import com.codetree.app.ui.AllDirections
import com.codetree.app.ui.LibrarySurface
import com.codetree.app.ui.primaryDirectionOf
import com.codetree.app.ui.onboarding.DirectionSelector
import com.codetree.data.content.ContentRepositoryImpl
import com.codetree.designsystem.CTButton
import com.codetree.designsystem.CTCard
import com.codetree.designsystem.CTDivider
import com.codetree.designsystem.CTIconButton
import com.codetree.designsystem.CTIcons
import com.codetree.designsystem.CTListRow
import com.codetree.designsystem.CTProgressRing
import com.codetree.designsystem.CTSectionHeader
import com.codetree.designsystem.CTSegmentedControl
import com.codetree.designsystem.CTSliderRow
import com.codetree.designsystem.CTSwitchRow
import com.codetree.designsystem.CTTag
import com.codetree.designsystem.CTTagTone
import com.codetree.designsystem.CTTextButton
import com.codetree.designsystem.CTValueRow
import com.codetree.designsystem.CodeTreeTheme
import com.codetree.domain.content.BilingualMode
import com.codetree.domain.content.ThemeMode
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * 「我的」Tab：学习统计 + 设置列表 + 知识库 / 关于 / 诊断入口。
 * 二级页（知识库、关于、诊断、AI 说明、学习方向）由本文件内 `route` 状态管理，
 * 不占用 App 的 Tab 状态，返回键落到 `onBack`。
 */
@Composable
fun ProfileSurface(
    container: AppContainer,
    snapshot: ContentRepositoryImpl.Snapshot,
    onRestartOnboarding: () -> Unit,
    onOpenAiTab: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var route by rememberSaveable { mutableStateOf(ROUTE_HOME) }

    Box(modifier = modifier.fillMaxSize()) {
        when (route) {
            ROUTE_LIBRARY -> LibraryPage(container = container, onBack = { route = ROUTE_HOME })
            ROUTE_ABOUT -> AboutPage(onBack = { route = ROUTE_HOME })
            ROUTE_DIAGNOSTICS -> DiagnosticsPage(container = container, onBack = { route = ROUTE_HOME })
            ROUTE_AI_INFO -> AiInfoPage(
                container = container,
                onBack = { route = ROUTE_HOME },
                onOpenAiTab = {
                    route = ROUTE_HOME
                    onOpenAiTab()
                },
            )

            ROUTE_DIRECTIONS -> DirectionsPage(
                container = container,
                currentDirection = container.settingsRepository.settings.value.activeDirection,
                onBack = { route = ROUTE_HOME },
            )

            else -> ProfileHomeScreen(
                container = container,
                snapshot = snapshot,
                onNavigate = { next -> route = next },
                onRestartOnboarding = onRestartOnboarding,
            )
        }
    }
}

@Composable
private fun ProfileHomeScreen(
    container: AppContainer,
    snapshot: ContentRepositoryImpl.Snapshot,
    onNavigate: (String) -> Unit,
    onRestartOnboarding: () -> Unit,
) {
    val settings by container.settingsRepository.settings.collectAsState()
    val progressMap by container.progressRepository.observeAll().collectAsState(initial = emptyMap())
    val scope = rememberCoroutineScope()
    val spacing = CodeTreeTheme.spacing

    val totalTopics = snapshot.topics.size
    val totalTerms = snapshot.terms.size
    val mastered = progressMap.values.count { it.status == com.codetree.domain.model.LearningStatus.MASTERED }
    val learning = progressMap.values.count { it.status == com.codetree.domain.model.LearningStatus.LEARNING }

    var fontScaleDraft by remember(settings.fontScale) { mutableStateOf(settings.fontScale) }

    Column(modifier = Modifier.fillMaxSize()) {
        AppPageHeader(title = stringResource(R.string.profile_title))
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = spacing.pageHorizontal),
        ) {
            // ---------------------------------------------------------- 学习统计
            CTSectionHeader(title = stringResource(R.string.profile_stats_section))
            CTCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val ratio = if (totalTopics == 0) 0f else mastered.toFloat() / totalTopics
                    CTProgressRing(
                        progress = ratio,
                        size = 76.dp,
                        strokeWidth = 7.dp,
                        label = "${(ratio * 100).roundToInt()}%",
                    )
                    Spacer(Modifier.width(spacing.lg))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "${stringResource(R.string.profile_mastered)}  $mastered / $totalTopics",
                            style = CodeTreeTheme.typography.headline,
                            color = CodeTreeTheme.colors.textPrimary,
                        )
                        Spacer(Modifier.height(spacing.xs))
                        Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                            CTTag(text = "${stringResource(R.string.profile_learning)} $learning", tone = CTTagTone.WARNING)
                            CTTag(
                                text = "${stringResource(R.string.profile_not_started)} ${(totalTopics - mastered - learning).coerceAtLeast(0)}",
                                tone = CTTagTone.NEUTRAL,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(spacing.md))
                CTDivider()
                Spacer(Modifier.height(spacing.md))
                Row(modifier = Modifier.fillMaxWidth()) {
                    StatBlock(
                        label = stringResource(R.string.profile_topics),
                        value = totalTopics.toString(),
                        modifier = Modifier.weight(1f),
                    )
                    StatBlock(
                        label = stringResource(R.string.profile_terms),
                        value = totalTerms.toString(),
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            // ---------------------------------------------------------- 设置
            CTSectionHeader(title = stringResource(R.string.profile_settings_section))
            CTCard(contentPadding = 0.dp) {
                CTListRow(
                    title = stringResource(R.string.settings_appearance),
                    onClick = {
                        val next = when (settings.themeMode) {
                            ThemeMode.SYSTEM -> ThemeMode.LIGHT
                            ThemeMode.LIGHT -> ThemeMode.DARK
                            ThemeMode.DARK -> ThemeMode.SYSTEM
                        }
                        scope.launch {
                            container.settingsRepository.update { it.copy(themeMode = next) }
                        }
                    },
                    trailing = {
                        CTTag(text = themeModeLabel(settings.themeMode), tone = CTTagTone.ACCENT)
                    },
                    showChevron = true,
                    leadingIcon = CTIcons.Sun,
                )
                CTDivider(inset = spacing.pageHorizontal)
                CTSliderRow(
                    title = stringResource(R.string.settings_font_scale),
                    value = fontScaleDraft,
                    onValueChange = { fontScaleDraft = it },
                    valueRange = 0.9f..1.6f,
                    valueLabel = "${(fontScaleDraft * 100).roundToInt()}%",
                    onValueChangeFinished = {
                        scope.launch {
                            container.settingsRepository.update { it.copy(fontScale = fontScaleDraft) }
                        }
                    },
                )
                CTDivider(inset = spacing.pageHorizontal)
                CTSwitchRow(
                    title = stringResource(R.string.settings_reduce_motion),
                    subtitle = stringResource(R.string.settings_reduce_motion_desc),
                    checked = settings.reduceMotion,
                    onCheckedChange = { checked ->
                        scope.launch {
                            container.settingsRepository.update { it.copy(reduceMotion = checked) }
                        }
                    },
                )
            }

            Spacer(Modifier.height(spacing.lg))
            CTCard {
                Text(
                    text = stringResource(R.string.settings_bilingual),
                    style = CodeTreeTheme.typography.headline,
                    color = CodeTreeTheme.colors.textPrimary,
                )
                Spacer(Modifier.height(spacing.md))
                CTSegmentedControl(
                    options = listOf(
                        stringResource(R.string.bilingual_zh_only),
                        stringResource(R.string.bilingual_en_grammar),
                        stringResource(R.string.bilingual_side_by_side),
                    ),
                    selectedIndex = settings.bilingualMode.ordinal,
                    onSelect = { index ->
                        val mode = BilingualMode.values().getOrNull(index)
                        if (mode != null) {
                            scope.launch {
                                container.settingsRepository.update { it.copy(bilingualMode = mode) }
                            }
                        }
                    },
                )
            }

            // ---------------------------------------------------------- 数据与入口
            CTSectionHeader(title = stringResource(R.string.settings_library))
            CTCard(contentPadding = 0.dp) {
                CTListRow(
                    title = stringResource(R.string.settings_ai),
                    subtitle = stringResource(R.string.settings_ai_desc),
                    leadingIcon = CTIcons.Assistant,
                    showChevron = true,
                    onClick = { onNavigate(ROUTE_AI_INFO) },
                )
                CTDivider(inset = spacing.pageHorizontal)
                CTListRow(
                    title = stringResource(R.string.settings_direction),
                    subtitle = directionLabel(settings.activeDirection),
                    leadingIcon = CTIcons.Graph,
                    showChevron = true,
                    onClick = { onNavigate(ROUTE_DIRECTIONS) },
                )
                CTDivider(inset = spacing.pageHorizontal)
                CTListRow(
                    title = stringResource(R.string.settings_library),
                    subtitle = stringResource(R.string.settings_library_desc),
                    leadingIcon = CTIcons.Book,
                    showChevron = true,
                    onClick = { onNavigate(ROUTE_LIBRARY) },
                )
                CTDivider(inset = spacing.pageHorizontal)
                CTListRow(
                    title = stringResource(R.string.settings_restart_onboarding),
                    leadingIcon = CTIcons.Refresh,
                    showChevron = true,
                    onClick = onRestartOnboarding,
                )
                CTDivider(inset = spacing.pageHorizontal)
                CTListRow(
                    title = stringResource(R.string.settings_about),
                    leadingIcon = CTIcons.Info,
                    showChevron = true,
                    onClick = { onNavigate(ROUTE_ABOUT) },
                )
                CTDivider(inset = spacing.pageHorizontal)
                CTListRow(
                    title = stringResource(R.string.settings_diagnostic),
                    subtitle = stringResource(R.string.settings_diagnostic_desc),
                    leadingIcon = CTIcons.Settings,
                    showChevron = true,
                    onClick = { onNavigate(ROUTE_DIAGNOSTICS) },
                )
            }

            Spacer(Modifier.height(spacing.xl))
            Text(
                text = "CodeTree ${AppContainer.DEFAULT_APP_VERSION}",
                style = CodeTreeTheme.typography.caption,
                color = CodeTreeTheme.colors.textTertiary,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(spacing.xxl))
        }
    }
}

// ---------------------------------------------------------------------------------------------
// 二级页：知识库
// ---------------------------------------------------------------------------------------------

@Composable
private fun LibraryPage(container: AppContainer, onBack: () -> Unit) {
    SecondaryPage(title = stringResource(R.string.settings_library), onBack = onBack) {
        LibrarySurface(
            library = container.libraryRepository,
            content = container.contentRepository,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

// ---------------------------------------------------------------------------------------------
// 二级页：关于
// ---------------------------------------------------------------------------------------------

@Composable
private fun AboutPage(onBack: () -> Unit) {
    val spacing = CodeTreeTheme.spacing
    SecondaryPage(title = stringResource(R.string.about_title), onBack = onBack) {
        CTCard {
            CTValueRow(title = stringResource(R.string.about_version), value = AppContainer.DEFAULT_APP_VERSION)
            Spacer(Modifier.height(spacing.md))
            Text(
                text = stringResource(R.string.about_body),
                style = CodeTreeTheme.typography.body,
                color = CodeTreeTheme.colors.textSecondary,
            )
            Spacer(Modifier.height(spacing.md))
            Text(
                text = stringResource(R.string.about_content_sources),
                style = CodeTreeTheme.typography.footnote,
                color = CodeTreeTheme.colors.textTertiary,
            )
        }
    }
}

// ---------------------------------------------------------------------------------------------
// 二级页：诊断（占位入口的真实数据：启动状态 / 警告 / 已安装包 / 存储）
// ---------------------------------------------------------------------------------------------

@Composable
private fun DiagnosticsPage(container: AppContainer, onBack: () -> Unit) {
    val spacing = CodeTreeTheme.spacing
    val contentReady by container.contentReady.collectAsState()
    val warnings by container.startupWarnings.collectAsState()
    val installed by produceState(initialValue = emptyList<com.codetree.domain.model.InstalledPack>()) {
        value = runCatching { container.codepackInstaller.readInstalled() }.getOrDefault(emptyList())
    }
    val storageBytes by produceState(initialValue = 0L) {
        value = runCatching { container.codepackInstaller.storageUsageBytes() }.getOrDefault(0L)
    }
    val snapshot by container.contentRepository.snapshots.collectAsState()

    SecondaryPage(title = stringResource(R.string.diagnostic_title), onBack = onBack) {
        CTCard(contentPadding = 0.dp) {
            CTValueRow(
                title = stringResource(R.string.diagnostic_content_ready),
                value = if (contentReady) stringResource(R.string.diagnostic_ready) else stringResource(R.string.diagnostic_not_ready),
            )
            CTDivider(inset = spacing.pageHorizontal)
            CTValueRow(
                title = stringResource(R.string.profile_topics),
                value = snapshot.topics.size.toString(),
            )
            CTDivider(inset = spacing.pageHorizontal)
            CTValueRow(
                title = stringResource(R.string.profile_terms),
                value = snapshot.terms.size.toString(),
            )
            CTDivider(inset = spacing.pageHorizontal)
            CTValueRow(
                title = stringResource(R.string.diagnostic_installed_packs),
                value = installed.size.toString(),
            )
            CTDivider(inset = spacing.pageHorizontal)
            CTValueRow(
                title = stringResource(R.string.diagnostic_storage),
                value = formatBytes(storageBytes),
            )
        }

        Spacer(Modifier.height(spacing.lg))
        CTSectionHeader(title = stringResource(R.string.diagnostic_warnings), modifier = Modifier.padding(horizontal = 0.dp))
        CTCard {
            if (warnings.isEmpty()) {
                Text(
                    text = stringResource(R.string.diagnostic_none),
                    style = CodeTreeTheme.typography.body,
                    color = CodeTreeTheme.colors.textSecondary,
                )
            } else {
                warnings.forEachIndexed { index, warning ->
                    if (index > 0) Spacer(Modifier.height(spacing.sm))
                    Text(
                        text = "· $warning",
                        style = CodeTreeTheme.typography.footnote,
                        color = CodeTreeTheme.colors.warning,
                    )
                }
            }
        }

        Spacer(Modifier.height(spacing.lg))
        CTCard {
            Text(
                text = "contentRoot: ${container.contentRoot.absolutePath}",
                style = CodeTreeTheme.typography.monoSmall,
                color = CodeTreeTheme.colors.textTertiary,
            )
            Spacer(Modifier.height(spacing.xs))
            Text(
                text = "workspace: ${container.workspaceDir.absolutePath}",
                style = CodeTreeTheme.typography.monoSmall,
                color = CodeTreeTheme.colors.textTertiary,
            )
        }
    }
}

// ---------------------------------------------------------------------------------------------
// 二级页：AI 设置说明（MVP：真正配置在 AI Tab 内）
// ---------------------------------------------------------------------------------------------

@Composable
private fun AiInfoPage(
    container: AppContainer,
    onBack: () -> Unit,
    onOpenAiTab: () -> Unit,
) {
    val settings by container.settingsRepository.settings.collectAsState()
    val keystoreAvailable = container.settingsRepositoryImpl.secretStoreAvailable
    val spacing = CodeTreeTheme.spacing

    SecondaryPage(title = stringResource(R.string.ai_info_title), onBack = onBack) {
        CTCard {
            Text(
                text = stringResource(R.string.ai_info_body),
                style = CodeTreeTheme.typography.body,
                color = CodeTreeTheme.colors.textSecondary,
            )
            Spacer(Modifier.height(spacing.md))
            CTTag(
                text = if (settings.hasApiKey) stringResource(R.string.ai_info_has_key) else stringResource(R.string.ai_info_no_key),
                tone = if (settings.hasApiKey) CTTagTone.SUCCESS else CTTagTone.NEUTRAL,
            )
            if (!keystoreAvailable) {
                Spacer(Modifier.height(spacing.sm))
                Text(
                    text = stringResource(R.string.ai_info_keystore_unavailable),
                    style = CodeTreeTheme.typography.footnote,
                    color = CodeTreeTheme.colors.warning,
                )
            }
        }
        Spacer(Modifier.height(spacing.lg))
        CTButton(
            text = stringResource(R.string.ai_info_go),
            onClick = onOpenAiTab,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

// ---------------------------------------------------------------------------------------------
// 二级页：学习方向重选
// ---------------------------------------------------------------------------------------------

@Composable
private fun DirectionsPage(
    container: AppContainer,
    currentDirection: String?,
    onBack: () -> Unit,
) {
    var selectedCsv by rememberSaveable {
        mutableStateOf(currentDirection.orEmpty())
    }
    val selected = remember(selectedCsv) {
        selectedCsv.split(',').filter { it.isNotBlank() }.toSet()
    }
    val scope = rememberCoroutineScope()

    SecondaryPage(title = stringResource(R.string.settings_direction), onBack = onBack) {
        DirectionSelector(
            selected = selected,
            onToggle = { id ->
                val next = selected.toMutableSet()
                if (!next.add(id)) next.remove(id)
                selectedCsv = next.joinToString(",")
            },
        )
        Spacer(Modifier.height(CodeTreeTheme.spacing.xl))
        CTButton(
            text = stringResource(R.string.action_done),
            enabled = selected.isNotEmpty(),
            onClick = {
                scope.launch {
                    container.settingsRepository.update {
                        it.copy(activeDirection = primaryDirectionOf(selected))
                    }
                }
                onBack()
            },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(CodeTreeTheme.spacing.xxl))
    }
}

// ---------------------------------------------------------------------------------------------
// 通用子组件
// ---------------------------------------------------------------------------------------------

@Composable
private fun AppPageHeader(title: String, onBack: (() -> Unit)? = null, subtitle: String? = null) {
    val spacing = CodeTreeTheme.spacing
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = spacing.pageHorizontal)
            .padding(top = spacing.xs, bottom = spacing.sm),
    ) {
        if (onBack != null) {
            Row(
                modifier = Modifier.fillMaxWidth().heightIn(min = spacing.minTouchTarget),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CTIconButton(
                    icon = CTIcons.Back,
                    contentDescription = stringResource(R.string.action_back),
                    onClick = onBack,
                )
            }
        }
        Text(
            text = title,
            style = CodeTreeTheme.typography.largeTitle,
            color = CodeTreeTheme.colors.textPrimary,
        )
        if (!subtitle.isNullOrBlank()) {
            Text(
                text = subtitle,
                style = CodeTreeTheme.typography.footnote,
                color = CodeTreeTheme.colors.textSecondary,
            )
        }
    }
}

@Composable
private fun SecondaryPage(
    title: String,
    onBack: () -> Unit,
    content: @Composable () -> Unit,
) {
    val spacing = CodeTreeTheme.spacing
    Column(modifier = Modifier.fillMaxSize()) {
        AppPageHeader(title = title, onBack = onBack)
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = spacing.pageHorizontal)
                .padding(bottom = spacing.xxl),
        ) {
            content()
        }
    }
}

@Composable
private fun StatBlock(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(
            text = value,
            style = CodeTreeTheme.typography.title2,
            color = CodeTreeTheme.colors.textPrimary,
        )
        Text(
            text = label,
            style = CodeTreeTheme.typography.footnote,
            color = CodeTreeTheme.colors.textSecondary,
        )
    }
}

@Composable
private fun themeModeLabel(mode: ThemeMode): String = when (mode) {
    ThemeMode.SYSTEM -> stringResource(R.string.theme_system)
    ThemeMode.LIGHT -> stringResource(R.string.theme_light)
    ThemeMode.DARK -> stringResource(R.string.theme_dark)
}

@Composable
private fun directionLabel(directionId: String?): String {
    val id = directionId.orEmpty()
    val option = AllDirections.firstOrNull { it.id == id }
    return when {
        id.isBlank() -> stringResource(R.string.profile_not_started)
        option != null -> stringResource(option.labelRes)
        else -> id
    }
}

private fun formatBytes(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "${bytes / 1024} KB"
    else -> String.format("%.1f MB", bytes / 1024.0 / 1024.0)
}

private const val ROUTE_HOME = "home"
private const val ROUTE_LIBRARY = "library"
private const val ROUTE_ABOUT = "about"
private const val ROUTE_DIAGNOSTICS = "diagnostics"
private const val ROUTE_AI_INFO = "ai_info"
private const val ROUTE_DIRECTIONS = "directions"

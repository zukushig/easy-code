package com.codetree.app.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.core.view.WindowCompat
import com.codetree.app.R
import com.codetree.app.di.AppContainer
import com.codetree.app.ui.onboarding.OnboardingScreen
import com.codetree.app.ui.screens.ProfileSurface
import com.codetree.designsystem.CTCrossFade
import com.codetree.designsystem.CTIcons
import com.codetree.designsystem.CTLoading
import com.codetree.designsystem.CTScaffold
import com.codetree.designsystem.CTTab
import com.codetree.designsystem.CTTabBar
import com.codetree.designsystem.CodeTreeTheme
import com.codetree.domain.ai.AiContextDraft
import com.codetree.domain.content.ThemeMode
import kotlinx.coroutines.launch

/**
 * App 组合根（唯一 Compose 入口）：
 *
 * 1. 读设置 → 套 [CodeTreeTheme]（深浅色 / 应用内字号 / 减少动效）+ 系统栏配色；
 * 2. 首次启动展示三页引导（onboardingDone 持久化，可跳过，可从设置重看）；
 * 3. 5 Tab 底部导航（学习 / 词典 / 终端 / AI / 我的）+ 跨 Tab 跳转参数：
 *    - learn.onOpenTerm → 词典 Tab + initialTermId
 *    - learn.onAskAi / terminal.onAskAi → AI Tab + initialDraft
 *    - dictionary.onOpenTopic → 学习 Tab + initialTopicId
 *    参数用 rememberSaveable 保存，切换 Tab 时清空（避免下次进入重复消费）。
 * 4. 内容索引未就绪时显示 CTLoading；就绪后各 feature 自管二级导航。
 */
@Composable
fun CodeTreeApp(container: AppContainer) {
    val settingsLoaded by container.settingsRepositoryImpl.isLoaded.collectAsState()
    val settings by container.settingsRepository.settings.collectAsState()
    val systemDark = isSystemInDarkTheme()
    val darkTheme = when (settings.themeMode) {
        ThemeMode.SYSTEM -> systemDark
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    CodeTreeTheme(
        darkTheme = darkTheme,
        fontScale = settings.fontScale,
        reduceMotion = settings.reduceMotion,
    ) {
        ApplySystemBarAppearance(darkTheme = darkTheme)

        if (!settingsLoaded) {
            CTScaffold { padding ->
                Box(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentAlignment = Alignment.Center,
                ) {
                    CTLoading(delayMs = 0, message = stringResource(R.string.loading))
                }
            }
        } else {
            var onboardingDone by rememberSaveable { mutableStateOf(settings.onboardingDone) }
            var replayOnboarding by rememberSaveable { mutableStateOf(false) }
            val showOnboarding = replayOnboarding || !onboardingDone
            val scope = rememberCoroutineScope()

            if (showOnboarding) {
                OnboardingScreen(
                    initialDirection = settings.activeDirection,
                    onComplete = { primaryDirection ->
                        onboardingDone = true
                        replayOnboarding = false
                        scope.launch {
                            container.settingsRepository.update { current ->
                                current.copy(
                                    onboardingDone = true,
                                    activeDirection = primaryDirection ?: current.activeDirection,
                                )
                            }
                        }
                    },
                )
            } else {
                MainTabs(
                    container = container,
                    onRestartOnboarding = { replayOnboarding = true },
                )
            }
        }
    }
}

@Composable
private fun MainTabs(
    container: AppContainer,
    onRestartOnboarding: () -> Unit,
) {
    val contentReady by container.contentReady.collectAsState()
    val snapshot by container.contentRepository.snapshots.collectAsState()

    var selectedTab by rememberSaveable { mutableStateOf(TAB_LEARN) }
    var pendingTermId by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingTopicId by rememberSaveable { mutableStateOf<String?>(null) }
    // AiContextDraft 不是 Parcelable（契约类型），进程死亡后无法恢复；其余跳转参数可恢复。
    var pendingDraft by remember { mutableStateOf<AiContextDraft?>(null) }
    val saveableStateHolder = rememberSaveableStateHolder()

    val tabs = listOf(
        CTTab(icon = CTIcons.Learn, label = stringResource(R.string.tab_learn)),
        CTTab(icon = CTIcons.Dictionary, label = stringResource(R.string.tab_dictionary)),
        CTTab(icon = CTIcons.Terminal, label = stringResource(R.string.tab_terminal)),
        CTTab(icon = CTIcons.Assistant, label = stringResource(R.string.tab_assistant)),
        CTTab(icon = CTIcons.Profile, label = stringResource(R.string.tab_profile)),
    )

    fun clearPending() {
        pendingTermId = null
        pendingTopicId = null
        pendingDraft = null
    }

    fun switchTab(index: Int) {
        if (index != selectedTab) {
            clearPending()
            selectedTab = index
        }
    }

    CTScaffold(
        bottomBar = {
            CTTabBar(
                tabs = tabs,
                selectedIndex = selectedTab,
                onSelect = { index ->
                    if (index != selectedTab) {
                        // 跨 Tab 参数消费后清空：离开目标 Tab 时重置，避免下次进入重复打开旧内容。
                        clearPending()
                        selectedTab = index
                    }
                },
            )
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (!contentReady) {
                CTLoading(
                    fullScreen = true,
                    delayMs = 0,
                    message = stringResource(R.string.content_loading),
                )
            } else {
                CTCrossFade(target = selectedTab, modifier = Modifier.fillMaxSize()) { target ->
                    val index = (target as? Int) ?: TAB_LEARN
                    saveableStateHolder.SaveableStateProvider(index) {
                        when (index) {
                            TAB_LEARN -> LearnSurface(
                                content = container.contentRepository,
                                progress = container.progressRepository,
                                settings = container.settingsRepository,
                                initialTopicId = pendingTopicId,
                                onOpenTerm = { termId ->
                                    pendingTermId = termId
                                    switchTab(TAB_DICTIONARY)
                                },
                                onAskAi = { draft ->
                                    pendingDraft = draft
                                    switchTab(TAB_ASSISTANT)
                                },
                            )

                            TAB_DICTIONARY -> DictionarySurface(
                                content = container.contentRepository,
                                progress = container.progressRepository,
                                settings = container.settingsRepository,
                                initialTermId = pendingTermId,
                                onOpenTopic = { topicId ->
                                    pendingTopicId = topicId
                                    switchTab(TAB_LEARN)
                                },
                                onOpenTerm = { termId -> pendingTermId = termId },
                            )

                            TAB_TERMINAL -> TerminalSurface(
                                content = container.contentRepository,
                                runtimeRegistry = container.runtimeRegistry,
                                workspaceDir = container.workspaceDir.absolutePath,
                                onAskAi = { draft ->
                                    pendingDraft = draft
                                    switchTab(TAB_ASSISTANT)
                                },
                            )

                            TAB_ASSISTANT -> AssistantSurface(
                                content = container.contentRepository,
                                settings = container.settingsRepository,
                                aiProvider = container.aiProvider,
                                sessionsDir = container.sessionsDir.absolutePath,
                                initialDraft = pendingDraft,
                            )

                            else -> ProfileSurface(
                                container = container,
                                snapshot = snapshot,
                                onRestartOnboarding = onRestartOnboarding,
                                onOpenAiTab = { switchTab(TAB_ASSISTANT) },
                            )
                        }
                    }
                }
            }
        }
    }
}

/** 系统栏图标明暗随主题（edge-to-edge 下状态/导航栏透明）。 */
@Composable
private fun ApplySystemBarAppearance(darkTheme: Boolean) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val activity = view.context.findActivity() ?: return@SideEffect
            val controller = WindowCompat.getInsetsController(activity.window, view)
            controller.isAppearanceLightStatusBars = !darkTheme
            controller.isAppearanceLightNavigationBars = !darkTheme
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

private const val TAB_LEARN = 0
private const val TAB_DICTIONARY = 1
private const val TAB_TERMINAL = 2
private const val TAB_ASSISTANT = 3
private const val TAB_PROFILE = 4

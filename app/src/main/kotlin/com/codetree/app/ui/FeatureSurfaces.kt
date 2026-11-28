package com.codetree.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.codetree.domain.ai.AiContextDraft
import com.codetree.domain.ai.AiProvider
import com.codetree.domain.content.ContentRepository
import com.codetree.domain.content.LibraryRepository
import com.codetree.domain.content.ProgressRepository
import com.codetree.domain.content.SettingsRepository
import com.codetree.runtime.api.RuntimeRegistry

/**
 * 5 个 Tab 的 feature 入口适配层（:app 与 feature 的唯一接触面）。
 *
 * 真实入口均已在各 feature 模块落盘，签名与 `docs/tasks/TASK-105.md` 契约一致；
 * 若某 feature 后续调整入口，只改本文件对应函数即可，导航层不用动。
 */
@Composable
fun LearnSurface(
    content: ContentRepository,
    progress: ProgressRepository,
    settings: SettingsRepository,
    initialTopicId: String?,
    onOpenTerm: (String) -> Unit,
    onAskAi: (AiContextDraft) -> Unit,
    modifier: Modifier = Modifier,
) {
    com.codetree.feature.learn.LearnTab(
        content = content,
        progress = progress,
        settings = settings,
        initialTopicId = initialTopicId,
        onOpenTerm = onOpenTerm,
        onAskAi = onAskAi,
        modifier = modifier,
    )
}

@Composable
fun DictionarySurface(
    content: ContentRepository,
    progress: ProgressRepository,
    settings: SettingsRepository,
    initialTermId: String?,
    onOpenTopic: (String) -> Unit,
    onOpenTerm: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    com.codetree.feature.dictionary.DictionaryTab(
        content = content,
        progress = progress,
        settings = settings,
        initialTermId = initialTermId,
        onOpenTopic = onOpenTopic,
        onOpenTerm = onOpenTerm,
        modifier = modifier,
    )
}

@Composable
fun TerminalSurface(
    content: ContentRepository,
    runtimeRegistry: RuntimeRegistry,
    workspaceDir: String,
    onAskAi: (AiContextDraft) -> Unit,
    modifier: Modifier = Modifier,
) {
    com.codetree.feature.terminal.ui.TerminalTab(
        content = content,
        runtimeRegistry = runtimeRegistry,
        workspaceDir = workspaceDir,
        onAskAi = onAskAi,
        modifier = modifier,
    )
}

@Composable
fun AssistantSurface(
    content: ContentRepository,
    settings: SettingsRepository,
    aiProvider: AiProvider,
    sessionsDir: String,
    initialDraft: AiContextDraft?,
    modifier: Modifier = Modifier,
) {
    com.codetree.feature.assistant.ui.AssistantTab(
        content = content,
        settings = settings,
        aiProvider = aiProvider,
        sessionsDir = sessionsDir,
        initialDraft = initialDraft,
        modifier = modifier,
    )
}

@Composable
fun LibrarySurface(
    library: LibraryRepository,
    content: ContentRepository,
    modifier: Modifier = Modifier,
) {
    com.codetree.feature.library.ui.LibraryTab(
        library = library,
        content = content,
        modifier = modifier,
    )
}

package com.codetree.feature.library.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.codetree.designsystem.CTErrorState
import com.codetree.designsystem.CTLoading
import com.codetree.designsystem.CTToast
import com.codetree.designsystem.CodeTreeTheme
import com.codetree.designsystem.LargeTitleBar
import com.codetree.domain.content.ContentRepository
import com.codetree.domain.content.LibraryRepository
import com.codetree.feature.library.library.LibraryViewModel
import com.codetree.feature.library.model.LibraryUiState

/**
 * 知识库 / 插件管理 Tab。
 *
 * `embedded = true`（默认）时只输出内容列，不带 Scaffold / 大标题，可放进 app「我的」页的
 * LazyColumn；`embedded = false` 时自带大标题与滚动容器。
 */
@Composable
fun LibraryTab(
    library: LibraryRepository,
    content: ContentRepository,
    modifier: Modifier = Modifier,
    embedded: Boolean = true,
) {
    val factory = remember(library, content) { LibraryViewModelFactory(library, content) }
    val viewModel: LibraryViewModel = viewModel(key = "library", factory = factory)
    val state by viewModel.state.collectAsState()

    if (embedded) {
        Box(modifier = modifier.fillMaxWidth()) {
            LibraryContent(state = state, viewModel = viewModel, modifier = Modifier.fillMaxWidth())
            NoticeToast(state = state, onDismiss = viewModel::dismissNotice, modifier = Modifier.align(Alignment.BottomCenter))
        }
    } else {
        Column(
            modifier = modifier
                .fillMaxSize()
                .background(CodeTreeTheme.colors.background)
                .verticalScroll(rememberScrollState()),
        ) {
            LargeTitleBar(title = "知识库", subtitle = "内容包与插件管理")
            LibraryContent(state = state, viewModel = viewModel, modifier = Modifier.fillMaxWidth())
        }
        Box(modifier = modifier.fillMaxSize()) {
            NoticeToast(state = state, onDismiss = viewModel::dismissNotice, modifier = Modifier.align(Alignment.BottomCenter))
        }
    }

    state.pendingDelete?.let { target ->
        if (target.hasDependents) {
            AlertDialog(
                onDismissRequest = viewModel::dismissPendingDelete,
                title = { Text("无法卸载「${target.title}」") },
                text = {
                    Text(
                        "已安装包依赖它：${target.dependents.joinToString("、")}。\n" +
                            "请先卸载或更新这些依赖包，再删除它。",
                    )
                },
                confirmButton = {
                    TextButton(onClick = viewModel::dismissPendingDelete) { Text("知道了") }
                },
            )
        } else {
            AlertDialog(
                onDismissRequest = viewModel::dismissPendingDelete,
                title = { Text("卸载「${target.title}」？") },
                text = { Text("将删除该内容包（v${target.pack.version}）。已学习进度不受影响，但相关知识/术语将不可用。") },
                confirmButton = {
                    TextButton(onClick = { viewModel.confirmRemove(target) }) { Text("卸载") }
                },
                dismissButton = {
                    TextButton(onClick = viewModel::dismissPendingDelete) { Text("取消") }
                },
            )
        }
    }
}

@Composable
private fun LibraryContent(
    state: LibraryUiState,
    viewModel: LibraryViewModel,
    modifier: Modifier = Modifier,
) {
    val spacing = CodeTreeTheme.spacing
    Column(modifier = modifier.padding(bottom = spacing.xxl)) {
        when {
            state.loading -> CTLoading(message = "正在加载知识目录…", modifier = Modifier.padding(top = spacing.xxl))
            state.loadError != null -> CTErrorState(
                error = state.loadError,
                onRetry = viewModel::load,
                modifier = Modifier.padding(top = spacing.md),
            )
            else -> {
                StorageSection(state = state)
                ImportSection(
                    state = state,
                    onImportBytes = viewModel::importCodepack,
                    onReadFailed = viewModel::importReadFailed,
                    onDismissResult = viewModel::dismissImportResult,
                )
                InstalledSection(
                    state = state,
                    onSetEnabled = viewModel::setEnabled,
                    onRemove = viewModel::requestRemove,
                )
                CatalogSection(
                    state = state,
                    onDownload = viewModel::download,
                    onCancelDownload = viewModel::cancelDownload,
                )
            }
        }
    }
}

@Composable
private fun NoticeToast(
    state: LibraryUiState,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    state.notice?.let { notice ->
        CTToast(message = notice, visible = true, onDismiss = onDismiss, modifier = modifier)
    }
}

private class LibraryViewModelFactory(
    private val library: LibraryRepository,
    private val content: ContentRepository,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        LibraryViewModel(library, content) as T
}

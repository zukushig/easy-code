package com.codetree.designsystem

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity

/**
 * 页面进入：淡入 + 轻微上移（≤ 12dp），每次 [key] 变化重新播放。
 * 减少动效时退化为纯淡入（无位移、无位移时长）。
 */
@Composable
fun CTPageEnter(
    key: Any? = Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val motion = CodeTreeTheme.motion
    val offsetPx = with(LocalDensity.current) { motion.enterOffsetDp.roundToPx() }
    var visible by remember(key) { mutableStateOf(false) }

    LaunchedEffect(key) { visible = true }

    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = fadeIn(animationSpec = tween(motion.normalMs)) +
            slideInVertically(animationSpec = tween(motion.normalMs)) { offsetPx },
        exit = fadeOut(animationSpec = tween(motion.fastMs)) +
            slideOutVertically(animationSpec = tween(motion.fastMs)) { -offsetPx },
    ) {
        content()
    }
}

/**
 * Tab 切换交叉淡入：reduceMotion 时退化为 0ms 直接切换（仍是 Crossfade，避免内容丢失）。
 */
@Composable
fun CTCrossFade(
    target: Any?,
    modifier: Modifier = Modifier,
    content: @Composable (Any?) -> Unit,
) {
    val motion = CodeTreeTheme.motion
    Crossfade(
        targetState = target,
        modifier = modifier,
        animationSpec = tween(motion.normalMs),
        label = "ct-cross-fade",
    ) { state ->
        content(state)
    }
}

/**
 * 显隐容器：默认淡入 + 轻微上移展开；减少动效时只淡入淡出。
 * 用于搜索栏展开、说明展开、离线横幅出现等局部状态。
 */
@Composable
fun CTAnimatedVisibility(
    visible: Boolean,
    modifier: Modifier = Modifier,
    expand: Boolean = true,
    content: @Composable () -> Unit,
) {
    val motion = CodeTreeTheme.motion
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = if (motion.reduceMotion || !expand) {
            fadeIn(animationSpec = tween(motion.fastMs))
        } else {
            fadeIn(animationSpec = tween(motion.normalMs)) +
                expandVertically(animationSpec = tween(motion.normalMs))
        },
        exit = if (motion.reduceMotion || !expand) {
            fadeOut(animationSpec = tween(motion.fastMs))
        } else {
            fadeOut(animationSpec = tween(motion.fastMs)) +
                shrinkVertically(animationSpec = tween(motion.fastMs))
        },
    ) {
        content()
    }
}

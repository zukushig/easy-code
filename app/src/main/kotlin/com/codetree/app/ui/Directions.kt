package com.codetree.app.ui

import androidx.annotation.StringRes
import com.codetree.app.R

/** 12 个学习方向，id 与内容包 `Topic.domain` 一一对应（见 content/packs）。 */
data class DirectionOption(
    val id: String,
    @StringRes val labelRes: Int,
)

val AllDirections: List<DirectionOption> = listOf(
    DirectionOption("programming-basics", R.string.direction_programming_basics),
    DirectionOption("computing-thinking", R.string.direction_computing_thinking),
    DirectionOption("python", R.string.direction_python),
    DirectionOption("javascript", R.string.direction_javascript),
    DirectionOption("git", R.string.direction_git),
    DirectionOption("data-structures", R.string.direction_data_structures),
    DirectionOption("kotlin", R.string.direction_kotlin),
    DirectionOption("android", R.string.direction_android),
    DirectionOption("web", R.string.direction_web),
    DirectionOption("networking", R.string.direction_networking),
    DirectionOption("ai-basics", R.string.direction_ai_basics),
    DirectionOption("vibe-coding", R.string.direction_vibe_coding),
)

/**
 * AppSettings.activeDirection 是单个 String（接口冻结），MVP 引导允许多选，
 * 但只持久化主方向（选中项中按目录顺序的第一个），保证 feature:learn 的
 * `domain == activeDirection` 过滤可用；其余选择在本次引导中仅用于展示。
 */
fun primaryDirectionOf(selected: Set<String>): String? =
    AllDirections.firstOrNull { it.id in selected }?.id

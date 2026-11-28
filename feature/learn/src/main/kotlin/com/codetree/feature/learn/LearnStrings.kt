package com.codetree.feature.learn

/**
 * 学习中文字符串常量（MVP）。
 *
 * TODO(T7/i18n)：后续轮次抽到 `res/values/strings.xml` 与 `LocalizedText`，
 * 本文件只用于让文案集中、避免散落在 Composable 里；代码关键字/术语保持英文原样。
 */
object LearnStrings {
    // 首页
    const val TITLE = "学习"
    const val DIRECTION_PREFIX = "学习方向"
    const val DIRECTION_ALL = "全部"
    const val VIEW_GRAPH = "图谱"
    const val VIEW_LIST = "列表"
    const val GROUP_BY_DOMAIN = "按方向"
    const val GROUP_BY_DIFFICULTY = "按难度"
    const val NEXT_UP_TITLE = "接下来学"
    const val NEXT_UP_SUBTITLE = "基于前置关系推荐，前置未完成的节点不会出现"
    const val NEXT_UP_EMPTY = "暂无推荐：先完成任意基础节点，或安装更多内容"
    const val LEGEND_TITLE = "关系图例"
    const val BACK_TO_CURRENT = "回到当前位置"
    const val FIT_VIEW = "复位视图"
    const val GRAPH_HINT = "双指缩放 · 单指拖动 · 点按选中节点 · 双击复位"
    const val GRAPH_EMPTY = "当前方向下没有知识节点"
    const val GRAPH_TOO_MANY = "节点较多，已聚焦当前视口"
    const val SELECTED_CARD_TITLE = "已选节点"
    const val OPEN_DETAIL = "查看详情"
    const val CLEAR_SELECTION = "取消选择"
    const val LIST_EMPTY = "当前方向下没有知识节点"
    const val LIST_TOOLTIP = "列表视图是图谱的等价入口，支持 TalkBack 顺序浏览"

    // 空/错/离线态
    const val EMPTY_CONTENT_TITLE = "还没有可学习的内容"
    const val EMPTY_CONTENT_DESC = "请到「我的 → 知识库」安装或导入内容包后重试"
    const val EMPTY_CONTENT_ACTION = "刷新内容"
    const val LOADING = "正在加载知识树…"
    const val OFFLINE_TITLE = "当前离线内容为空"
    const val OFFLINE_DESC = "已安装内容支持离线学习；连接网络后可到「我的 → 知识库」下载更多内容包"

    // 进度
    const val STATUS_NOT_STARTED = "未学"
    const val STATUS_LEARNING = "学习中"
    const val STATUS_MASTERED = "已掌握"
    const val MARK_LEARNING = "标记为学习中"
    const val MARK_MASTERED = "标记为已掌握"
    const val UNMARK_MASTERED = "已掌握（点按取消）"
    const val STATUS_SAVED = "进度已保存"
    const val PROGRESS_SAVE_FAILED = "进度保存失败"

    // 难度
    const val DIFFICULTY_BEGINNER = "入门"
    const val DIFFICULTY_INTERMEDIATE = "进阶"
    const val DIFFICULTY_ADVANCED = "高级"

    // 节点详情
    const val DETAIL_TITLE = "节点详情"
    const val OBJECTIVES = "学习目标"
    const val OBJECTIVES_EMPTY = "本节点暂无学习目标"
    const val EXPLANATION = "讲解"
    const val EXPLANATION_EMPTY = "本节点暂无讲解内容"
    const val EXAMPLES = "双语示例"
    const val EXAMPLES_EMPTY = "本节点暂无示例"
    const val TERMS = "关联术语"
    const val TERMS_EMPTY = "本节点暂无关联术语"
    const val EXERCISE = "练习"
    const val EXERCISE_EMPTY = "本节点暂无练习"
    const val EXERCISE_SKIP_REASON = "未提供练习的原因："
    const val SHOW_HINT = "查看提示"
    const val HIDE_HINT = "收起提示"
    const val RELATIONS = "关联节点"
    const val RELATION_PREREQUISITE = "前置"
    const val RELATION_RELATED = "相关"
    const val RELATION_APPLIED = "应用"
    const val RELATION_CONFUSED = "易混淆"
    const val RELATION_EMPTY = "本节点暂无关联节点"
    const val MISSING_PREREQUISITES = "前置未满足"
    const val MISSING_PREREQUISITES_DESC = "完成以下前置节点后可解锁本节点："
    const val ASK_AI = "问 AI"
    const val ASK_AI_EMPTY = "AI 未配置或示例为空"
    const val BACK = "返回"
    const val NOT_FOUND = "内容中找不到该节点"
    const val NOT_FOUND_DESC = "内容包可能已更新或卸载，请返回知识树重新选择"
    const val ESTIMATED_MINUTES = "约 %d 分钟"
    const val DETAIL_CODECARD_RUNNABLE = "可运行"
    const val DETAIL_CODECARD_READONLY = "仅阅读"
    const val ANNOTATION_HINT = "点按高亮行查看注释"
    const val ANNOTATION_EMPTY = "本示例没有注释"

    // 关系图例文案
    const val EDGE_PREREQUISITE = "前置（实线+箭头）"
    const val EDGE_RELATED = "相关（虚线）"
    const val EDGE_APPLIED = "应用（点线）"
    const val EDGE_CONFUSED = "易混淆（双色短线）"

    fun statusLabel(status: com.codetree.domain.model.LearningStatus): String = when (status) {
        com.codetree.domain.model.LearningStatus.NOT_STARTED -> STATUS_NOT_STARTED
        com.codetree.domain.model.LearningStatus.LEARNING -> STATUS_LEARNING
        com.codetree.domain.model.LearningStatus.MASTERED -> STATUS_MASTERED
    }

    fun difficultyLabel(difficulty: com.codetree.domain.model.Difficulty): String = when (difficulty) {
        com.codetree.domain.model.Difficulty.BEGINNER -> DIFFICULTY_BEGINNER
        com.codetree.domain.model.Difficulty.INTERMEDIATE -> DIFFICULTY_INTERMEDIATE
        com.codetree.domain.model.Difficulty.ADVANCED -> DIFFICULTY_ADVANCED
    }
}

package com.codetree.feature.dictionary

import com.codetree.domain.model.ReviewStatus
import com.codetree.domain.model.TermType

/**
 * 词典中文字符串常量（MVP）。英文语法区与代码关键字保持英文原样（DIC-005）。
 *
 * TODO(T7/i18n)：后续轮次抽到 `res/values/strings.xml`；本文件先集中管理文案。
 */
object DictionaryStrings {
    // 搜索页
    const val TITLE = "词典"
    const val SEARCH_PLACEHOLDER = "搜索术语：中文 / English / 缩写 / 别名"
    const val SEARCH_HINT = "离线全文检索 · 精确匹配优先于前缀，前缀优先于模糊建议"
    const val CANCEL = "取消"
    const val CLEAR = "清空"
    const val HISTORY_TITLE = "最近搜索"
    const val HISTORY_EMPTY = "暂无搜索历史"
    const val CLEAR_HISTORY = "清空历史"
    const val FAVORITES_TITLE = "收藏"
    const val FAVORITES_EMPTY = "暂无收藏：在搜索结果中点按书签按钮收藏"
    const val HOT_TITLE = "热词"
    const val HOT_EMPTY = "暂无术语：请到「我的 → 知识库」安装内容包"
    const val NO_RESULT_TITLE = "没有找到匹配的术语"
    const val NO_RESULT_DESC_PREFIX = "没有与「"
    const val NO_RESULT_DESC_SUFFIX = "」匹配的术语；可尝试更短的关键词或检查拼写"
    const val SUGGESTIONS_TITLE = "你可能想找"
    const val SUGGESTION_BADGE = "拼写建议"
    const val SEARCHING = "正在搜索…"
    const val SEARCH_ELAPSED = "耗时 %d ms"
    const val SEARCH_INVALID = "请输入至少 1 个字母或数字（最长 64 个字符）"
    const val GROUP_EXACT = "精确匹配"
    const val GROUP_ALIAS = "别名匹配"
    const val GROUP_PREFIX = "前缀匹配"
    const val GROUP_FTS = "全文匹配"
    const val GROUP_FUZZY = "你可能想找"
    const val MATCHED_FIELD = "命中字段："
    const val FAVORITE_ADD = "收藏"
    const val FAVORITE_REMOVE = "取消收藏"
    const val EMPTY_CONTENT_TITLE = "还没有可检索的术语"
    const val EMPTY_CONTENT_DESC = "请到「我的 → 知识库」安装内容包后重试"
    const val REFRESH = "重试"
    const val OFFLINE_TITLE = "离线检索可用"
    const val OFFLINE_DESC = "已安装内容支持离线全文检索；连接网络不影响本页"

    // 术语详情
    const val DETAIL_TITLE = "术语详情"
    const val BACK = "返回"
    const val MODE_ZH = "中文解释"
    const val MODE_EN = "英文语法"
    const val MODE_BILINGUAL = "双语对照"
    const val DEFINITION_TITLE = "定义"
    const val DEFINITION_ZH_EMPTY = "本术语暂无中文解释"
    const val DEFINITION_EN_EMPTY = "No English grammar notes yet"
    const val SYNTAX_TITLE = "语法格式"
    const val SYNTAX_EMPTY = "本术语暂无语法规则"
    const val SYNTAX_LANGUAGE = "语言："
    const val SYNTAX_VERSION = "语法版本："
    const val SYNTAX_KEYWORDS = "关键字："
    const val SYNTAX_COMMENT = "行注释："
    const val SYNTAX_BRACKETS = "括号："
    const val EXAMPLES_TITLE = "示例"
    const val EXAMPLES_EMPTY = "本术语暂无示例"
    const val ERRORS_TITLE = "常见错误"
    const val ERRORS_EMPTY = "暂无常见错误规则"
    const val RELATED_TERMS_TITLE = "相关术语"
    const val RELATED_TERMS_EMPTY = "暂无相关术语"
    const val RELATED_TOPICS_TITLE = "关联知识节点"
    const val RELATED_TOPICS_EMPTY = "暂无关联知识节点"
    const val ALIASES_TITLE = "别名"
    const val ALIASES_EMPTY = "暂无别名"
    const val NOT_FOUND = "找不到该术语"
    const val NOT_FOUND_DESC = "内容包可能已更新或卸载，请返回词典重新搜索"
    const val FOOTER_VERSION = "内容版本 %s · 来源 %s"
    const val RUNNABLE = "可运行"
    const val READ_ONLY = "仅阅读"
    const val EXPECTED_OUTPUT = "预期输出"
    const val ANNOTATION_TITLE = "注释"
    const val ANNOTATION_EMPTY = "本示例没有注释"
    const val ANNOTATION_HINT = "点按注释条目查看中英说明"

    fun termTypeLabel(type: TermType): String = when (type) {
        TermType.CONCEPT -> "概念"
        TermType.SYNTAX -> "语法"
        TermType.API -> "API"
        TermType.TOOL -> "工具"
    }

    fun reviewStatusLabel(status: ReviewStatus): String = when (status) {
        ReviewStatus.DRAFT -> "草稿"
        ReviewStatus.AI_DRAFT -> "AI 草稿（未审核）"
        ReviewStatus.REVIEWED -> "已审核"
        ReviewStatus.VERIFIED_EXAMPLE -> "示例已验证"
    }
}

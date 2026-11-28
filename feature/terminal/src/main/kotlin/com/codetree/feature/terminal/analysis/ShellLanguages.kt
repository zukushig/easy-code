package com.codetree.feature.terminal.analysis

import com.codetree.feature.terminal.model.LanguageOption

/**
 * 终端输入的高亮语言。
 *
 * - [SHELL]：安全 shell 本身没有内容包语法文件，使用 [ShellCommandCatalog] 的内置规则；
 * - Python / JavaScript：运行时未安装时仍可用于**阅读高亮**（示例语言），语法来自内容包
 *   `LanguageSyntax`，不内嵌语言规则。
 */
object ShellLanguages {
    const val SHELL: String = "safe-shell"

    val OPTIONS: List<LanguageOption> = listOf(
        LanguageOption(id = SHELL, label = "安全 Shell"),
        LanguageOption(id = "python", label = "Python"),
        LanguageOption(id = "javascript", label = "JavaScript"),
    )

    fun labelOf(id: String): String = OPTIONS.firstOrNull { it.id == id }?.label ?: id
}

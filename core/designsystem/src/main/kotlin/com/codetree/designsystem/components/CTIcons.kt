package com.codetree.designsystem

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.ImageVector.Builder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * CodeTree 自绘细线图标集（24×24 viewport、1.5dp 描边、圆角端点）。
 *
 * 不依赖 `material-icons-extended`（体积与低内存构建考虑）；所有图标只有描边、无填充，
 * 由 `Icon(tint = ...)` 着色，因此深浅主题、选中/未选中态都只需换 tint。
 */
object CTIcons {

    // ---------------------------------------------------------------- 5 个 Tab

    /** 学习：知识树（一个根节点 + 两个子节点 + 连线）。 */
    val Learn: ImageVector by icon("ct.learn") {
        circle(12f, 5f, 2.2f)
        circle(6f, 18f, 2.2f)
        circle(18f, 18f, 2.2f)
        moveTo(11f, 7f); lineTo(7f, 16f)
        moveTo(13f, 7f); lineTo(17f, 16f)
        moveTo(8f, 18f); lineTo(16f, 18f)
    }

    /** 词典：翻开的书。 */
    val Dictionary: ImageVector by icon("ct.dictionary") {
        moveTo(12f, 6.5f)
        curveTo(10f, 5f, 7f, 4.5f, 4f, 4.5f)
        lineTo(4f, 18.5f)
        curveTo(7f, 18.5f, 10f, 19f, 12f, 20.5f)
        curveTo(14f, 19f, 17f, 18.5f, 20f, 18.5f)
        lineTo(20f, 4.5f)
        curveTo(17f, 4.5f, 14f, 5f, 12f, 6.5f)
        moveTo(12f, 6.5f); lineTo(12f, 20.5f)
    }

    /** 终端：窗口 + 提示符 + 光标线。 */
    val Terminal: ImageVector by icon("ct.terminal") {
        moveTo(5f, 5f); lineTo(19f, 5f)
        curveTo(20.1f, 5f, 21f, 5.9f, 21f, 7f)
        lineTo(21f, 17f)
        curveTo(21f, 18.1f, 20.1f, 19f, 19f, 19f)
        lineTo(5f, 19f)
        curveTo(3.9f, 19f, 3f, 18.1f, 3f, 17f)
        lineTo(3f, 7f)
        curveTo(3f, 5.9f, 3.9f, 5f, 5f, 5f)
        close()
        moveTo(7f, 9.5f); lineTo(10.5f, 12f); lineTo(7f, 14.5f)
        moveTo(12f, 15f); lineTo(17f, 15f)
    }

    /** AI：四角星 + 小星。 */
    val Assistant: ImageVector by icon("ct.assistant") {
        moveTo(11f, 3f)
        lineTo(12.8f, 9.2f)
        lineTo(19f, 11f)
        lineTo(12.8f, 12.8f)
        lineTo(11f, 19f)
        lineTo(9.2f, 12.8f)
        lineTo(3f, 11f)
        lineTo(9.2f, 9.2f)
        close()
        moveTo(18f, 3f); lineTo(18.9f, 5.1f); lineTo(21f, 6f); lineTo(18.9f, 6.9f); lineTo(18f, 9f)
        lineTo(17.1f, 6.9f); lineTo(15f, 6f); lineTo(17.1f, 5.1f); close()
    }

    /** 我的：人像（头 + 肩）。 */
    val Profile: ImageVector by icon("ct.profile") {
        circle(12f, 8f, 3.5f)
        moveTo(5f, 20f)
        curveTo(5f, 16.4f, 8.1f, 14f, 12f, 14f)
        curveTo(15.9f, 14f, 19f, 16.4f, 19f, 20f)
    }

    // ---------------------------------------------------------------- 通用操作

    val Search: ImageVector by icon("ct.search") {
        circle(10.5f, 10.5f, 6.5f)
        moveTo(15.5f, 15.5f); lineTo(20.5f, 20.5f)
    }

    val Close: ImageVector by icon("ct.close") {
        moveTo(6f, 6f); lineTo(18f, 18f)
        moveTo(18f, 6f); lineTo(6f, 18f)
    }

    val ChevronRight: ImageVector by icon("ct.chevron-right") {
        moveTo(9f, 5f); lineTo(16f, 12f); lineTo(9f, 19f)
    }

    val ChevronLeft: ImageVector by icon("ct.chevron-left") {
        moveTo(15f, 5f); lineTo(8f, 12f); lineTo(15f, 19f)
    }

    val ChevronDown: ImageVector by icon("ct.chevron-down") {
        moveTo(5f, 9f); lineTo(12f, 16f); lineTo(19f, 9f)
    }

    val ChevronUp: ImageVector by icon("ct.chevron-up") {
        moveTo(5f, 15f); lineTo(12f, 8f); lineTo(19f, 15f)
    }

    /** 返回：iOS 风格尖括号（比箭头更细）。 */
    val Back: ImageVector by icon("ct.back") {
        moveTo(14.5f, 4.5f); lineTo(7f, 12f); lineTo(14.5f, 19.5f)
    }

    val Check: ImageVector by icon("ct.check") {
        moveTo(5f, 12.5f); lineTo(10f, 17.5f); lineTo(19f, 7f)
    }

    val Plus: ImageVector by icon("ct.plus") {
        moveTo(12f, 5f); lineTo(12f, 19f)
        moveTo(5f, 12f); lineTo(19f, 12f)
    }

    val Minus: ImageVector by icon("ct.minus") {
        moveTo(5f, 12f); lineTo(19f, 12f)
    }

    val Settings: ImageVector by icon("ct.settings") {
        circle(12f, 12f, 3f)
        circle(12f, 12f, 7f)
        moveTo(12f, 2.5f); lineTo(12f, 5f)
        moveTo(12f, 19f); lineTo(12f, 21.5f)
        moveTo(2.5f, 12f); lineTo(5f, 12f)
        moveTo(19f, 12f); lineTo(21.5f, 12f)
        moveTo(5.3f, 5.3f); lineTo(7.1f, 7.1f)
        moveTo(16.9f, 16.9f); lineTo(18.7f, 18.7f)
        moveTo(18.7f, 5.3f); lineTo(16.9f, 7.1f)
        moveTo(7.1f, 16.9f); lineTo(5.3f, 18.7f)
    }

    val Info: ImageVector by icon("ct.info") {
        circle(12f, 12f, 9f)
        moveTo(12f, 10.5f); lineTo(12f, 16.5f)
        moveTo(12f, 7.2f); lineTo(12f, 7.6f)
    }

    val Warning: ImageVector by icon("ct.warning") {
        moveTo(12f, 3.5f); lineTo(21.5f, 20f); lineTo(2.5f, 20f); close()
        moveTo(12f, 9f); lineTo(12f, 14.5f)
        moveTo(12f, 17.2f); lineTo(12f, 17.6f)
    }

    val ErrorCircle: ImageVector by icon("ct.error") {
        circle(12f, 12f, 9f)
        moveTo(8.5f, 8.5f); lineTo(15.5f, 15.5f)
        moveTo(15.5f, 8.5f); lineTo(8.5f, 15.5f)
    }

    val Refresh: ImageVector by icon("ct.refresh") {
        moveTo(20f, 12f)
        curveTo(20f, 16.4f, 16.4f, 20f, 12f, 20f)
        curveTo(7.6f, 20f, 4f, 16.4f, 4f, 12f)
        curveTo(4f, 7.6f, 7.6f, 4f, 12f, 4f)
        curveTo(15.1f, 4f, 17.8f, 5.8f, 19.1f, 8.4f)
        moveTo(19.5f, 3.5f); lineTo(19.5f, 8.5f); lineTo(14.5f, 8.5f)
    }

    val Download: ImageVector by icon("ct.download") {
        moveTo(12f, 3.5f); lineTo(12f, 15f)
        moveTo(7f, 10f); lineTo(12f, 15f); lineTo(17f, 10f)
        moveTo(4f, 17f); lineTo(4f, 20f); lineTo(20f, 20f); lineTo(20f, 17f)
    }

    val Trash: ImageVector by icon("ct.trash") {
        moveTo(5f, 7f); lineTo(19f, 7f)
        moveTo(9f, 7f); lineTo(9f, 4.5f); lineTo(15f, 4.5f); lineTo(15f, 7f)
        moveTo(6.5f, 7f); lineTo(7.5f, 20f); lineTo(16.5f, 20f); lineTo(17.5f, 7f)
        moveTo(10f, 10.5f); lineTo(10f, 16.5f)
        moveTo(14f, 10.5f); lineTo(14f, 16.5f)
    }

    // ---------------------------------------------------------------- 学习与词典

    val Book: ImageVector by icon("ct.book") {
        moveTo(5f, 4.5f); lineTo(16f, 4.5f)
        curveTo(17.1f, 4.5f, 18f, 5.4f, 18f, 6.5f)
        lineTo(18f, 19.5f); lineTo(6.5f, 19.5f)
        curveTo(5.7f, 19.5f, 5f, 18.8f, 5f, 18f)
        lineTo(5f, 4.5f)
        close()
        moveTo(5f, 17f); lineTo(18f, 17f)
        moveTo(9f, 8.5f); lineTo(14f, 8.5f)
        moveTo(9f, 12f); lineTo(14f, 12f)
    }

    /** 词典释义：文档 + 行。 */
    val Document: ImageVector by icon("ct.document") {
        moveTo(6f, 3.5f); lineTo(14.5f, 3.5f); lineTo(19f, 8f); lineTo(19f, 20.5f); lineTo(6f, 20.5f); close()
        moveTo(14.5f, 3.5f); lineTo(14.5f, 8f); lineTo(19f, 8f)
        moveTo(9f, 12f); lineTo(16f, 12f)
        moveTo(9f, 15.5f); lineTo(16f, 15.5f)
    }

    val Play: ImageVector by icon("ct.play") {
        moveTo(8f, 4.5f); lineTo(19f, 12f); lineTo(8f, 19.5f); close()
    }

    val Stop: ImageVector by icon("ct.stop") {
        moveTo(7f, 7f); lineTo(17f, 7f); lineTo(17f, 17f); lineTo(7f, 17f); close()
    }

    val Send: ImageVector by icon("ct.send") {
        moveTo(4f, 12f); lineTo(20f, 4.5f); lineTo(15f, 20f); lineTo(11.5f, 13.5f); close()
        moveTo(11.5f, 13.5f); lineTo(20f, 4.5f)
    }

    val Code: ImageVector by icon("ct.code") {
        moveTo(8.5f, 8f); lineTo(4f, 12.5f); lineTo(8.5f, 17f)
        moveTo(15.5f, 8f); lineTo(20f, 12.5f); lineTo(15.5f, 17f)
    }

    val Graph: ImageVector by icon("ct.graph") {
        circle(5f, 18f, 1.8f)
        circle(12f, 12f, 1.8f)
        circle(19f, 6f, 1.8f)
        circle(19f, 18f, 1.8f)
        moveTo(6.3f, 16.6f); lineTo(10.7f, 13.4f)
        moveTo(13.4f, 10.9f); lineTo(17.6f, 7.1f)
        moveTo(12f, 13.8f); lineTo(18.2f, 16.7f)
    }

    val Bookmark: ImageVector by icon("ct.bookmark") {
        moveTo(6f, 4f); lineTo(18f, 4f); lineTo(18f, 20f); lineTo(12f, 15.5f); lineTo(6f, 20f); close()
    }

    // ---------------------------------------------------------------- 状态 / 空态

    val Inbox: ImageVector by icon("ct.inbox") {
        moveTo(4f, 12.5f); lineTo(8f, 12.5f); lineTo(9.5f, 15f); lineTo(14.5f, 15f); lineTo(16f, 12.5f)
        lineTo(20f, 12.5f)
        moveTo(4f, 12.5f); lineTo(6.5f, 5f); lineTo(17.5f, 5f); lineTo(20f, 12.5f)
        curveTo(20f, 16.9f, 16.4f, 20.5f, 12f, 20.5f)
        curveTo(7.6f, 20.5f, 4f, 16.9f, 4f, 12.5f)
        close()
    }

    val Offline: ImageVector by icon("ct.offline") {
        moveTo(3f, 3f); lineTo(21f, 21f)
        moveTo(5f, 9.5f); curveTo(7f, 7.7f, 9.4f, 6.7f, 12f, 6.7f)
        curveTo(14.6f, 6.7f, 17f, 7.7f, 19f, 9.5f)
        moveTo(8f, 13f); curveTo(9.1f, 11.9f, 10.5f, 11.3f, 12f, 11.3f)
        moveTo(12f, 18f); lineTo(12f, 18.4f)
    }

    val Cloud: ImageVector by icon("ct.cloud") {
        moveTo(7f, 18f); curveTo(4.2f, 18f, 2.5f, 16.2f, 2.5f, 13.8f)
        curveTo(2.5f, 11.5f, 4.2f, 9.8f, 6.4f, 9.6f)
        curveTo(7.1f, 6.8f, 9.4f, 5f, 12.2f, 5f)
        curveTo(15.5f, 5f, 18.1f, 7.4f, 18.4f, 10.5f)
        curveTo(20.5f, 10.8f, 21.5f, 12.3f, 21.5f, 14.2f)
        curveTo(21.5f, 16.4f, 19.9f, 18f, 17.6f, 18f)
        lineTo(7f, 18f)
        close()
    }

    // ---------------------------------------------------------------- 主题 / 显示

    val Sun: ImageVector by icon("ct.sun") {
        circle(12f, 12f, 4.2f)
        moveTo(12f, 2.5f); lineTo(12f, 5f)
        moveTo(12f, 19f); lineTo(12f, 21.5f)
        moveTo(2.5f, 12f); lineTo(5f, 12f)
        moveTo(19f, 12f); lineTo(21.5f, 12f)
        moveTo(5.3f, 5.3f); lineTo(7.1f, 7.1f)
        moveTo(16.9f, 16.9f); lineTo(18.7f, 18.7f)
        moveTo(18.7f, 5.3f); lineTo(16.9f, 7.1f)
        moveTo(7.1f, 16.9f); lineTo(5.3f, 18.7f)
    }

    val Moon: ImageVector by icon("ct.moon") {
        moveTo(20f, 14.5f)
        curveTo(18.6f, 16.7f, 16.1f, 18.2f, 13.3f, 18.2f)
        curveTo(9.1f, 18.2f, 5.7f, 14.8f, 5.7f, 10.6f)
        curveTo(5.7f, 8.4f, 6.6f, 6.4f, 8.1f, 4.9f)
        curveTo(7.8f, 5.8f, 7.6f, 6.8f, 7.6f, 7.9f)
        curveTo(7.6f, 12.1f, 11f, 15.5f, 15.2f, 15.5f)
        curveTo(16.9f, 15.5f, 18.5f, 14.9f, 20f, 14.5f)
        close()
    }

    val Lock: ImageVector by icon("ct.lock") {
        moveTo(6f, 10.5f); lineTo(18f, 10.5f); lineTo(18f, 20.5f); lineTo(6f, 20.5f); close()
        moveTo(8.5f, 10.5f); lineTo(8.5f, 7.5f)
        curveTo(8.5f, 5.6f, 10.1f, 4f, 12f, 4f)
        curveTo(13.9f, 4f, 15.5f, 5.6f, 15.5f, 7.5f)
        lineTo(15.5f, 10.5f)
    }

    val More: ImageVector by icon("ct.more") {
        circle(5f, 12f, 1.4f)
        circle(12f, 12f, 1.4f)
        circle(19f, 12f, 1.4f)
    }

    val ExternalLink: ImageVector by icon("ct.external-link") {
        moveTo(13f, 4.5f); lineTo(19.5f, 4.5f); lineTo(19.5f, 11f)
        moveTo(19.5f, 4.5f); lineTo(10.5f, 13.5f)
        moveTo(17f, 14f); lineTo(17f, 19.5f); lineTo(4.5f, 19.5f); lineTo(4.5f, 7f); lineTo(10f, 7f)
    }

    // ---------------------------------------------------------------- 构建工具

    private fun icon(name: String, draw: PathBuilder.() -> Unit): Lazy<ImageVector> = lazy {
        Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                draw()
            }
        }.build()
    }

    /** 主屏底部 Tab 图标按顺序取用；标签文字由调用方本地化。 */
    fun tabIcons(): List<ImageVector> = listOf(Learn, Dictionary, Terminal, Assistant, Profile)
}

// PathBuilder 的圆弧近似工具：手绘圆形（四段三次贝塞尔，误差 < 0.2%）。
private fun PathBuilder.circle(cx: Float, cy: Float, r: Float) {
    val k = r * 0.5522848f
    moveTo(cx, cy - r)
    curveTo(cx + k, cy - r, cx + r, cy - k, cx + r, cy)
    curveTo(cx + r, cy + k, cx + k, cy + r, cx, cy + r)
    curveTo(cx - k, cy + r, cx - r, cy + k, cx - r, cy)
    curveTo(cx - r, cy - k, cx - k, cy - r, cx, cy - r)
    close()
}

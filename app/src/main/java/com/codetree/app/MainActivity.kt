package com.codetree.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.codetree.app.ui.CodeTreeApp

/**
 * 唯一 Activity：edge-to-edge + 单一 Compose 根（5 Tab 导航在 Compose 内完成，不用 NavHost 承载 Tab）。
 * 依赖容器由 [CodeTreeApplication] 创建，这里通过参数传给 Compose 根，不做全局静态访问。
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val container = (application as CodeTreeApplication).container
        setContent {
            CodeTreeApp(container)
        }
    }
}

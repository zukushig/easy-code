package com.codetree.app

import android.app.Application
import com.codetree.app.di.AppContainer

/**
 * 应用入口：持有手写依赖容器 [AppContainer]（ADR-009，M1 暂缓 Hilt），
 * 启动时在后台协程执行「种子内容安装 → 内容索引重建」，UI 通过 `container.contentReady`
 * 决定显示加载态还是正式内容；失败不崩溃，App 仍可展示引导与设置。
 */
class CodeTreeApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.start()
    }
}

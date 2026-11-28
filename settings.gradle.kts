// EasyCode / CodeTree —— Android 模块入口
// 依赖仓库优先使用国内镜像（本机 aarch64 环境直连 maven central 不可用），后接官方源兜底。
pluginManagement {
    repositories {
        maven("https://maven.aliyun.com/repository/google")
        maven("https://maven.aliyun.com/repository/central")
        maven("https://maven.aliyun.com/repository/gradle-plugin")
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        maven("https://maven.aliyun.com/repository/google")
        maven("https://maven.aliyun.com/repository/central")
        google()
        mavenCentral()
    }
}

rootProject.name = "easy-code"

include(
    ":app",
    ":core:domain",
    ":core:data",
    ":core:designsystem",
    ":plugin-api",
    ":runtime:api",
    ":runtime:safe-shell",
    ":feature:learn",
    ":feature:dictionary",
    ":feature:terminal",
    ":feature:assistant",
    ":feature:library",
)

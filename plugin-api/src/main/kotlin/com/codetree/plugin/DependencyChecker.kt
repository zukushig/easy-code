package com.codetree.plugin

/**
 * 依赖检查器（docs/specs/plugin-api.md §4）：给定待安装包清单与已安装包集合，
 * 检查缺失依赖、版本范围、重复 packId 冲突与依赖环。
 *
 * 纯函数、不安装任何包；返回 `CodepackValidationResult`，`errors` 非空即不得安装。
 * 只处理包级依赖；内容实体 ID（Topic/Term/Example）冲突由数据层合并索引时检查。
 *
 * 稳定错误码：`dependency.missing` / `dependency.optional_missing`（WARNING）/
 * `dependency.version_conflict` / `dependency.cycle` / `dependency.duplicate_pack` /
 * `dependency.update_pack`（WARNING）/ `dependency.invalid_range` / `dependency.invalid_pack_id` /
 * `dependency.invalid_version` / `dependency.self_reference` / `dependency.duplicate`。
 */
class DependencyChecker {

    /** 已安装包摘要：版本与自身依赖（用于检测跨包依赖环）。 */
    data class InstalledPackSummary(
        val packId: String,
        val version: String,
        val dependencies: List<PackDependency> = emptyList(),
    )

    /** 便捷入口：只关心已安装版本时使用 `packId -> version`。 */
    fun check(manifest: PackManifest, installed: Map<String, String>): CodepackValidationResult =
        check(manifest, installed.map { InstalledPackSummary(it.key, it.value) })

    fun check(manifest: PackManifest, installedPacks: List<InstalledPackSummary>): CodepackValidationResult {
        val issues = mutableListOf<ValidationIssue>()
        val installed = installedPacks.associateBy { it.packId }

        // 重复安装：同版本视为冲突；不同版本按“更新”处理（WARNING，由 UI 提示覆盖安装）。
        val existing = installed[manifest.packId]
        if (existing != null) {
            if (existing.version == manifest.version) {
                issues += error(
                    manifest.packId,
                    "dependency.duplicate_pack",
                    "packId ${manifest.packId} 的 ${manifest.version} 版本已安装，不能重复安装",
                )
            } else {
                issues += warning(
                    manifest.packId,
                    "dependency.update_pack",
                    "packId ${manifest.packId} 已安装 ${existing.version}，本次为更新到 ${manifest.version}",
                )
            }
        }

        val seenDeps = HashSet<String>()
        for (dependency in manifest.dependencies) {
            if (!isValidPackId(dependency.packId)) {
                issues += error(
                    dependency.packId,
                    "dependency.invalid_pack_id",
                    "依赖 packId 不合法：${dependency.packId}",
                )
                continue
            }
            if (!seenDeps.add(dependency.packId)) {
                issues += error(dependency.packId, "dependency.duplicate", "依赖重复声明：${dependency.packId}")
                continue
            }
            if (dependency.packId == manifest.packId) {
                issues += error(manifest.packId, "dependency.self_reference", "包不能依赖自身")
                continue
            }
            val range = try {
                VersionRange(dependency.versionRange)
            } catch (e: IllegalArgumentException) {
                issues += error(
                    dependency.packId,
                    "dependency.invalid_range",
                    "版本范围不可解析：${dependency.versionRange}（${e.message}）",
                )
                null
            } ?: continue

            val target = installed[dependency.packId]
            if (target == null) {
                if (dependency.optional) {
                    issues += warning(
                        dependency.packId,
                        "dependency.optional_missing",
                        "可选依赖 ${dependency.packId} 未安装，相关能力不可用",
                    )
                } else {
                    issues += error(
                        dependency.packId,
                        "dependency.missing",
                        "缺少必需依赖 ${dependency.packId}（${dependency.versionRange}）",
                    )
                }
                continue
            }
            val version = SemVer.parse(target.version)
            if (version == null) {
                issues += error(
                    dependency.packId,
                    "dependency.invalid_version",
                    "已安装版本不是合法 SemVer：${target.version}",
                )
                continue
            }
            if (!range.contains(version)) {
                issues += error(
                    dependency.packId,
                    "dependency.version_conflict",
                    "已安装 ${dependency.packId}@${target.version} 不满足范围 ${dependency.versionRange}",
                )
            }
        }

        issues += detectDependencyCycles(manifest, installed)
        return CodepackValidationResult(manifest = manifest, issues = issues)
    }

    /**
     * 从待安装包出发检测依赖环：图节点 = 待安装包 + 已安装包，边 = 依赖声明。
     * 只报告与待安装包可达的环（不相关包之间的历史环由数据层诊断）。
     */
    private fun detectDependencyCycles(
        manifest: PackManifest,
        installed: Map<String, InstalledPackSummary>,
    ): List<ValidationIssue> {
        val adjacency = HashMap<String, List<String>>()
        adjacency[manifest.packId] = manifest.dependencies.map { it.packId }.filter { it != manifest.packId }
        for (summary in installed.values) {
            adjacency[summary.packId] = summary.dependencies.map { it.packId }
        }

        val state = HashMap<String, Int>() // 0/缺省=未访问 1=访问中 2=完成
        val stack = ArrayList<String>()
        val issues = mutableListOf<ValidationIssue>()

        fun dfs(node: String) {
            state[node] = 1
            stack.add(node)
            for (next in adjacency[node].orEmpty().sorted()) {
                if (next !in adjacency) continue
                when (state[next] ?: 0) {
                    0 -> dfs(next)
                    1 -> {
                        val start = stack.indexOf(next)
                        if (start >= 0) {
                            val chain = stack.subList(start, stack.size).toList() + next
                            issues += error(
                                chain.first(),
                                "dependency.cycle",
                                "依赖环：${chain.joinToString(" -> ")}",
                            )
                        }
                    }

                    else -> Unit
                }
            }
            stack.removeAt(stack.lastIndex)
            state[node] = 2
        }

        if (manifest.packId in adjacency) dfs(manifest.packId)
        return issues
    }

    private companion object {
        fun isValidPackId(raw: String): Boolean =
            raw.length in 1..120 && Regex("^[a-z0-9][a-z0-9.-]{0,119}$").matches(raw)

        fun error(path: String, code: String, message: String): ValidationIssue =
            ValidationIssue(path, code, ValidationSeverity.ERROR, message)

        fun warning(path: String, code: String, message: String): ValidationIssue =
            ValidationIssue(path, code, ValidationSeverity.WARNING, message)
    }
}

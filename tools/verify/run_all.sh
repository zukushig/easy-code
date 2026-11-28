#!/usr/bin/env bash
# CodeTree 验收一键入口：内容/仓库/种子/文档/工作流五类检查。
#
# 用法：
#   bash tools/verify/run_all.sh              # 完整验收
#   bash tools/verify/run_all.sh --selftest   # 先跑各检查器自测，再做完整验收
#
# 退出码：0 = 全部通过；1 = 至少一项失败（失败项会打印 FAIL，并继续跑完其余项）。

set -uo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT" || exit 1

RUN_SELFTEST=0
for argument in "$@"; do
    case "$argument" in
        --selftest) RUN_SELFTEST=1 ;;
        -h|--help)
            sed -n '2,12p' "$0"
            exit 0
            ;;
        *)
            echo "未知参数：$argument" >&2
            exit 2
            ;;
    esac
done

FAILED=0

run_step() {
    local name="$1"
    shift
    echo ""
    echo "== ${name} =="
    if "$@"; then
        echo "PASS ${name}"
    else
        echo "FAIL ${name}"
        FAILED=1
    fi
}

if [ "$RUN_SELFTEST" = "1" ]; then
    run_step "自测 check_content" python3 tools/verify/check_content.py --selftest
    run_step "自测 check_repo" python3 tools/verify/check_repo.py --selftest
    run_step "自测 check_seed" python3 tools/verify/check_seed.py --selftest
fi

run_step "内容与分发产物" python3 tools/verify/check_content.py
run_step "仓库结构与门禁" python3 tools/verify/check_repo.py
run_step "内置种子一致性" python3 tools/verify/check_seed.py
run_step "文档合集时效性" python3 scripts/merge_docs.py --check
run_step "工作流结构与策略" python3 scripts/validate_workflows.py
run_step "内容工具校验器" python3 tools/content/validate_content.py

echo ""
if [ "$FAILED" = "0" ]; then
    echo "验收结果：全部通过"
    exit 0
fi
echo "验收结果：存在失败项（见上方 FAIL）"
exit 1

#!/bin/bash
# 批量为 CIMC UC1 eval tasks 添加 MODEL_BASED grader
# 前置条件：后端已部署 PATCH /api/eval/v1/suites/{suiteId}/tasks/{taskId} 端点
#
# 用法：COOKIE="synapse_session=xxx" ./scripts/update-cimc-model-graders.sh

set -euo pipefail

BASE="https://forge.delivery/api/eval/v1"
SUITE_ID="ce1311c0-a732-4692-b7a1-3ed2e53b56a0"

if [ -z "${COOKIE:-}" ]; then
  echo "ERROR: 请设置 COOKIE 环境变量，如: COOKIE='synapse_session=xxx' $0"
  exit 1
fi

# 获取所有 task
echo "=== 获取 CIMC UC1 所有 tasks ==="
TASKS=$(curl -s -b "$COOKIE" "$BASE/suites/$SUITE_ID/tasks")
TASK_COUNT=$(echo "$TASKS" | python3 -c "import sys,json; print(len(json.load(sys.stdin)))")
echo "共 $TASK_COUNT 个 task"

# 定义每个 task 名称 → MODEL_BASED rubric 的映射
# 使用 Python 脚本来处理 JSON 操作
python3 << 'PYEOF'
import json, subprocess, sys, os

COOKIE = os.environ["COOKIE"]
BASE = "https://forge.delivery/api/eval/v1"
SUITE_ID = "ce1311c0-a732-4692-b7a1-3ed2e53b56a0"

# MODEL_BASED rubric 定义（按 task 名称匹配）
MODEL_RUBRICS = {
    "紧急插单 — 基础场景": [
        {"criterion": "场景完整性", "weight": 0.90, "description": "是否完整覆盖产能评估、物料检查、排产调整等全流程", "scale": [0.0, 0.25, 0.5, 0.75, 1.0]},
        {"criterion": "多策略对比", "weight": 0.90, "description": "是否提供交期优先、成本优先等多种策略并对比优劣", "scale": [0.0, 0.25, 0.5, 0.75, 1.0]},
        {"criterion": "瓶颈感知", "weight": 0.85, "description": "是否识别瓶颈工序并评估其对插单的制约", "scale": [0.0, 0.25, 0.5, 0.75, 1.0]},
        {"criterion": "物料齐套检查", "weight": 0.85, "description": "是否检查物料可用性并纳入方案约束", "scale": [0.0, 0.25, 0.5, 0.75, 1.0]},
        {"criterion": "可执行性", "weight": 0.85, "description": "方案是否具体到班次、人员安排等可直接执行的细节", "scale": [0.0, 0.25, 0.5, 0.75, 1.0]},
    ],
    "紧急插单 — 物料短缺场景": [
        {"criterion": "物料风险识别", "weight": 0.95, "description": "是否准确识别物料短缺项及其影响程度", "scale": [0.0, 0.25, 0.5, 0.75, 1.0]},
        {"criterion": "供应链感知", "weight": 0.90, "description": "是否考虑在途物料、到货时间、供应商响应等供应链因素", "scale": [0.0, 0.25, 0.5, 0.75, 1.0]},
        {"criterion": "可行替代方案", "weight": 0.85, "description": "是否给出分批交付、替代物料等务实可行的应对方案", "scale": [0.0, 0.25, 0.5, 0.75, 1.0]},
        {"criterion": "时间线准确性", "weight": 0.85, "description": "交付时间预测是否合理，是否考虑物料到货对工期的影响", "scale": [0.0, 0.25, 0.5, 0.75, 1.0]},
        {"criterion": "跨系统协调", "weight": 0.80, "description": "是否协调 APS 排产与 SAP 物料两个系统的数据给出联合方案", "scale": [0.0, 0.25, 0.5, 0.75, 1.0]},
    ],
    "紧急插单 — 多单冲突场景": [
        {"criterion": "优先级推理", "weight": 0.95, "description": "优先级排序是否有清晰的逻辑支撑（客户等级、交期、换型成本等多维度权衡）", "scale": [0.0, 0.25, 0.5, 0.75, 1.0]},
        {"criterion": "冲突解决", "weight": 0.90, "description": "是否给出明确的冲突解决方案而非模棱两可的建议", "scale": [0.0, 0.25, 0.5, 0.75, 1.0]},
        {"criterion": "瓶颈分析", "weight": 0.90, "description": "是否量化分析瓶颈工序在多单并行时的负荷情况", "scale": [0.0, 0.25, 0.5, 0.75, 1.0]},
        {"criterion": "客户等级感知", "weight": 0.85, "description": "是否在决策中体现客户等级差异的商业影响", "scale": [0.0, 0.25, 0.5, 0.75, 1.0]},
        {"criterion": "换型成本分析", "weight": 0.80, "description": "是否考虑不同产品类型切换的时间和成本代价", "scale": [0.0, 0.25, 0.5, 0.75, 1.0]},
    ],
    "异常诊断 — 设备故障": [
        {"criterion": "根因诊断", "weight": 0.90, "description": "是否基于知识库和历史案例给出可能的根因分析", "scale": [0.0, 0.25, 0.5, 0.75, 1.0]},
        {"criterion": "OEE分解", "weight": 0.85, "description": "是否从可用率/性能率/良品率三因子角度分析停机影响", "scale": [0.0, 0.25, 0.5, 0.75, 1.0]},
        {"criterion": "级联影响分析", "weight": 0.90, "description": "是否分析瓶颈停机对上下游工序和全线产出的级联影响", "scale": [0.0, 0.25, 0.5, 0.75, 1.0]},
        {"criterion": "可执行处置方案", "weight": 0.85, "description": "处置建议是否具体、分级（紧急/短期/长期），可直接执行", "scale": [0.0, 0.25, 0.5, 0.75, 1.0]},
        {"criterion": "紧迫性感知", "weight": 0.85, "description": "是否体现时间敏感性，量化每分钟停机损失", "scale": [0.0, 0.25, 0.5, 0.75, 1.0]},
    ],
    "异常诊断 — 物料质量问题": [
        {"criterion": "缺陷范围分析", "weight": 0.95, "description": "是否准确界定受影响的物料批次、数量和工作中心范围", "scale": [0.0, 0.25, 0.5, 0.75, 1.0]},
        {"criterion": "追溯能力", "weight": 0.90, "description": "是否追溯已流入下游工序的不良品，给出追溯路径", "scale": [0.0, 0.25, 0.5, 0.75, 1.0]},
        {"criterion": "质量处置", "weight": 0.90, "description": "处置方案是否涵盖隔离、筛选、退货、返工等完整措施", "scale": [0.0, 0.25, 0.5, 0.75, 1.0]},
        {"criterion": "供应链响应", "weight": 0.85, "description": "是否联动库存查询替代料源，给出供应链层面的应对", "scale": [0.0, 0.25, 0.5, 0.75, 1.0]},
        {"criterion": "下游保护", "weight": 0.85, "description": "是否给出防止不良品继续流转的即时保护措施", "scale": [0.0, 0.25, 0.5, 0.75, 1.0]},
    ],
    "异常诊断 — OEE 骤降分析": [
        {"criterion": "三因子分解", "weight": 0.95, "description": "是否完整分解可用率、性能率、良品率三因子并分别量化", "scale": [0.0, 0.25, 0.5, 0.75, 1.0]},
        {"criterion": "根因准确性", "weight": 0.90, "description": "是否准确定位导致 OEE 下降的主要因子和根因", "scale": [0.0, 0.25, 0.5, 0.75, 1.0]},
        {"criterion": "量化分析", "weight": 0.85, "description": "分析是否有具体数据支撑，而非泛泛而谈", "scale": [0.0, 0.25, 0.5, 0.75, 1.0]},
        {"criterion": "纠正措施", "weight": 0.85, "description": "改善建议是否针对根因，而非散弹枪式罗列", "scale": [0.0, 0.25, 0.5, 0.75, 1.0]},
        {"criterion": "数据驱动推理", "weight": 0.85, "description": "推理过程是否基于数据证据，而非经验猜测", "scale": [0.0, 0.25, 0.5, 0.75, 1.0]},
    ],
    "人效分析 — 缺员借调": [
        {"criterion": "缺口识别", "weight": 0.95, "description": "是否准确识别各工作中心的人员盈缺状态", "scale": [0.0, 0.25, 0.5, 0.75, 1.0]},
        {"criterion": "技能匹配准确性", "weight": 0.95, "description": "借调建议是否严格匹配技能要求，不出现资质不符的安排", "scale": [0.0, 0.25, 0.5, 0.75, 1.0]},
        {"criterion": "借调可行性", "weight": 0.90, "description": "方案是否考虑借出方的产能影响，确保不拆东墙补西墙", "scale": [0.0, 0.25, 0.5, 0.75, 1.0]},
        {"criterion": "安全合规", "weight": 0.90, "description": "是否确保特种工种的资质证书要求得到满足", "scale": [0.0, 0.25, 0.5, 0.75, 1.0]},
        {"criterion": "效率影响评估", "weight": 0.80, "description": "是否评估借调后对整体人均效率（人/台）的影响", "scale": [0.0, 0.25, 0.5, 0.75, 1.0]},
    ],
    "人效分析 — 加班合规审查": [
        {"criterion": "合规意识", "weight": 0.95, "description": "是否主动检查劳动法加班时长限制并据此约束方案", "scale": [0.0, 0.25, 0.5, 0.75, 1.0]},
        {"criterion": "疲劳风险评估", "weight": 0.90, "description": "是否识别累计工时接近上限或连续加班的疲劳风险人员", "scale": [0.0, 0.25, 0.5, 0.75, 1.0]},
        {"criterion": "精确人数", "weight": 0.85, "description": "是否给出可加班/不可加班的精确人数而非模糊表述", "scale": [0.0, 0.25, 0.5, 0.75, 1.0]},
        {"criterion": "法律知识", "weight": 0.85, "description": "引用的法规条款是否准确（如月加班36小时上限）", "scale": [0.0, 0.25, 0.5, 0.75, 1.0]},
        {"criterion": "工人福祉", "weight": 0.80, "description": "是否在产能需求与工人身心健康之间寻求平衡", "scale": [0.0, 0.25, 0.5, 0.75, 1.0]},
    ],
    "节拍分析 — 瓶颈诊断": [
        {"criterion": "瓶颈识别", "weight": 0.95, "description": "是否准确定位制约整线节拍的瓶颈工序", "scale": [0.0, 0.25, 0.5, 0.75, 1.0]},
        {"criterion": "约束分解", "weight": 0.90, "description": "是否分析瓶颈的约束来源（设备/人员/工艺）", "scale": [0.0, 0.25, 0.5, 0.75, 1.0]},
        {"criterion": "节拍差距量化", "weight": 0.90, "description": "是否量化目标节拍与实际节拍的差距及其影响", "scale": [0.0, 0.25, 0.5, 0.75, 1.0]},
        {"criterion": "改善优先级", "weight": 0.85, "description": "改善建议是否有优先级排序，聚焦 ROI 最高的措施", "scale": [0.0, 0.25, 0.5, 0.75, 1.0]},
        {"criterion": "TOC思维", "weight": 0.80, "description": "是否体现约束理论（TOC）的系统思维，而非局部优化", "scale": [0.0, 0.25, 0.5, 0.75, 1.0]},
    ],
    "节拍分析 — 线平衡优化": [
        {"criterion": "线平衡分析", "weight": 0.90, "description": "是否给出线平衡率数据并识别不平衡的关键段", "scale": [0.0, 0.25, 0.5, 0.75, 1.0]},
        {"criterion": "WIP根因", "weight": 0.90, "description": "是否从节拍差异角度解释 WIP 堆积的根本原因", "scale": [0.0, 0.25, 0.5, 0.75, 1.0]},
        {"criterion": "节拍分布清晰度", "weight": 0.85, "description": "各工序节拍数据是否清晰呈现，便于快速理解", "scale": [0.0, 0.25, 0.5, 0.75, 1.0]},
        {"criterion": "优化可行性", "weight": 0.85, "description": "平衡优化建议是否考虑实施约束（设备改造周期、人员技能等）", "scale": [0.0, 0.25, 0.5, 0.75, 1.0]},
        {"criterion": "精益思维", "weight": 0.80, "description": "是否体现精益生产理念（消除浪费、拉动式生产）", "scale": [0.0, 0.25, 0.5, 0.75, 1.0]},
    ],
    "跨技能集成 — 插单+异常联合场景": [
        {"criterion": "双问题协调", "weight": 0.95, "description": "是否将插单和异常作为相互关联的问题统筹处理，而非独立应对", "scale": [0.0, 0.25, 0.5, 0.75, 1.0]},
        {"criterion": "优先级推理", "weight": 0.90, "description": "两个紧急事项的处理顺序是否有充分的逻辑支撑", "scale": [0.0, 0.25, 0.5, 0.75, 1.0]},
        {"criterion": "异常对产能影响", "weight": 0.90, "description": "是否量化异常导致的产能下降对插单可行性的影响", "scale": [0.0, 0.25, 0.5, 0.75, 1.0]},
        {"criterion": "可行集成方案", "weight": 0.85, "description": "最终方案是否在异常约束下仍为插单找到可行路径", "scale": [0.0, 0.25, 0.5, 0.75, 1.0]},
        {"criterion": "风险意识", "weight": 0.85, "description": "是否识别并量化方案执行中的关键风险和应急预案", "scale": [0.0, 0.25, 0.5, 0.75, 1.0]},
    ],
    "跨技能集成 — 晨会全景调度": [
        {"criterion": "覆盖完整性", "weight": 0.95, "description": "是否完整覆盖产出/排产/异常/人员/物料/关注事项6个维度", "scale": [0.0, 0.25, 0.5, 0.75, 1.0]},
        {"criterion": "信息准确性", "weight": 0.90, "description": "各维度数据是否来自正确的系统查询，而非凭空编造", "scale": [0.0, 0.25, 0.5, 0.75, 1.0]},
        {"criterion": "简洁清晰", "weight": 0.90, "description": "报告是否简洁有力，适合5分钟内汇报完，避免冗余", "scale": [0.0, 0.25, 0.5, 0.75, 1.0]},
        {"criterion": "重点突出", "weight": 0.85, "description": "是否突出今日需重点关注的风险和行动项", "scale": [0.0, 0.25, 0.5, 0.75, 1.0]},
        {"criterion": "跨系统集成", "weight": 0.85, "description": "是否有效整合 MOM/APS/SAP/OPE 多系统数据形成全景视图", "scale": [0.0, 0.25, 0.5, 0.75, 1.0]},
    ],
}

# 获取现有 tasks
import urllib.request
req = urllib.request.Request(
    f"{BASE}/suites/{SUITE_ID}/tasks",
    headers={"Cookie": COOKIE}
)
with urllib.request.urlopen(req) as resp:
    tasks = json.loads(resp.read())

print(f"获取到 {len(tasks)} 个 task")

success = 0
fail = 0
for task in tasks:
    task_id = task["id"]
    task_name = task["name"]

    if task_name not in MODEL_RUBRICS:
        print(f"  SKIP: {task_name} (无 MODEL_BASED rubric 定义)")
        continue

    # 获取现有 graderConfigs，追加 MODEL_BASED
    existing_configs = task.get("graderConfigs", [])

    # 检查是否已有 MODEL_BASED
    has_model = any(c.get("type") == "MODEL_BASED" for c in existing_configs)
    if has_model:
        print(f"  SKIP: {task_name} (已有 MODEL_BASED grader)")
        continue

    # 构造新的 graderConfigs（保留现有 + 追加 MODEL_BASED）
    new_configs = existing_configs + [{
        "type": "MODEL_BASED",
        "model": "claude-sonnet-4-6",
        "assertions": [],
        "rubric": MODEL_RUBRICS[task_name]
    }]

    # PATCH 更新
    patch_data = json.dumps({"graderConfigs": new_configs}).encode("utf-8")
    patch_req = urllib.request.Request(
        f"{BASE}/suites/{SUITE_ID}/tasks/{task_id}",
        data=patch_data,
        headers={
            "Cookie": COOKIE,
            "Content-Type": "application/json"
        },
        method="PATCH"
    )

    try:
        with urllib.request.urlopen(patch_req) as resp:
            result = json.loads(resp.read())
            grader_count = len(result.get("graderConfigs", []))
            print(f"  OK: {task_name} → {grader_count} graders (CODE_BASED + MODEL_BASED)")
            success += 1
    except Exception as e:
        print(f"  FAIL: {task_name} → {e}")
        fail += 1

print(f"\n=== 完成: {success} 成功, {fail} 失败 ===")
PYEOF

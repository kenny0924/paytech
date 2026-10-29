#!/usr/bin/env python3
"""REQ-03 阶段 5 演示数据：经真实运营 API 把两个已提交的个人入驻案件审完并批准（四眼：管理员推荐、合成复核人批准）。
  M：case 241532757572866048 → CRR MEDIUM，按行业默认 MCC
  H：case 241533467257491456 → CRR HIGH，MCC 5967（t_ref_mcc.network_high_risk=1）
  H2：case 241533546651471872 → CRR HIGH，按行业默认 MCC（复核批准路径用；H 留作待复核演示）
用法：python3 -I prepare_cases.py [M,H]；可重跑（已批准的跳过）。
"""
import argparse
import json
import sys
import time
from pathlib import Path

sys.dont_write_bytecode = True
sys.path.insert(0, str(Path(__file__).resolve().parent))
import common as C  # noqa: E402

OA = C.OA
CASES = {"M": ("241532757572866048", "MEDIUM", None), "H": ("241533467257491456", "HIGH", "5967"),
         "H2": ("241533546651471872", "HIGH", None)}


def classify(ops, case_id, mcc):
    detail = OA.case_detail(ops, case_id)
    child = next(c for category in detail.get("industries") or [] for c in category.get("children") or [])
    status, reply = OA.command(ops, "PUT", f"/cases/{case_id}/classifications",
                               {"items": [{"industryCode": child["industryCode"], "mccCode": mcc or child.get("defaultMccCode") or "5999",
                                           "primary": True}]})
    OA.check("PREP", f"{case_id} 设置分类 MCC {mcc or child.get('defaultMccCode')}", status == 200, f"HTTP {status} {OA.problem(reply)}")
    status, reply = OA.command(ops, "POST", f"/cases/{case_id}/classification-confirmation", None)
    OA.check("PREP", f"{case_id} 确认分类", status == 200, f"HTTP {status} {OA.problem(reply)}")


def approve(ops, reviewer, case_id, rating, mcc):
    if OA.review_of(OA.case_detail(ops, case_id)).get("status") == "APPROVED":
        OA.record("PREP", f"{case_id} 已批准", "INFO", "skip")
        return
    OA.wait_screening(ops, case_id)
    OA.accept_and_pass(ops, "PREP", case_id, None, case_id)
    if not OA.case_detail(ops, case_id).get("classificationConfirmed"):
        classify(ops, case_id, mcc)
    # 确认分类后立刻评 CRR 在本机偶发 409 CONCURRENT_REQUEST（compliance 既有行为，与本需求无关）：稍等再评。
    time.sleep(3)
    OA.rate(ops, case_id, None, case_id, rating)
    status, reply = OA.command(ops, "POST", f"/cases/{case_id}/recommendation",
                               {"recommendation": "APPROVE", "note": "rq03 stage5", "onBehalfOfPartnerId": None})
    OA.check("PREP", f"{case_id} 推荐 APPROVE", status == 200, f"HTTP {status} {OA.problem(reply)}")
    blockers = OA.review_of(OA.case_detail(reviewer, case_id)).get("approvalBlockers") or []
    status, reply = OA.command(reviewer, "POST", f"/cases/{case_id}/approval",
                               {"reasonCode": "DOCUMENTS_VERIFIED", "reason": "rq03 stage5 four-eyes", "onBehalfOfPartnerId": None})
    OA.check("PREP", f"{case_id} 复核人批准", status == 200, f"HTTP {status} {OA.problem(reply)} blockers={blockers}")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("only", nargs="?", default="M,H")
    args = parser.parse_args()
    ops = C.admin()
    ctx = {"opsA": ops, "run": time.strftime("%m%d%H%M%S"), "args": argparse.Namespace(env="local", create_reviewer=True)}
    reviewer = OA.reviewer(ctx)
    for label in args.only.split(","):
        case_id, rating, mcc = CASES[label]
        approve(ops, reviewer, case_id, rating, mcc)
    print(json.dumps([r for r in OA.RESULTS if r.get("verdict") == "FAIL"], ensure_ascii=False, indent=1))


main()

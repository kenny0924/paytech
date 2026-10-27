#!/usr/bin/env python3
"""参考数据目录与 client-master 迁移 · mor-test 验收（runbook 第 ⑬ 步，主会话执行；rel-dev 2026-10-27 准备，未运行）。

复用阶段 5 本机验收 verification/local-e2e/refdata_acceptance.py（读入源码后改写，不复制场景），改动只为"不在 mor-test 留下验收以外的写入"：
  - E2E_TARGET=test：运营 http://localhost:28080、Client https://localhost:28443；Platform 私网接口经接入 mor-test-backend 的一次性容器
    （PrivateHttpProbe，镜像 E2E_PROBE_IMAGE，缺省为本次 Platform 发布镜像 ID，只用其中的 JRE）。
  - 去掉全部"root SQL 临时把参考数据置 INACTIVE"的子步骤（A 的 EGP / AE-UQ / AQ，K 的 MCC 5655），改记 SKIP；停用场景已由本机验收覆盖
    （acceptance-local-20261027T113104Z.json / T113334Z.json）。不存在编码（XYZ、AE-ZZ、EG-C 国家不一致、XX、RETAIL_NOPE、0001）照常验证。
  - 去掉 C 中"新建 DRAFT 币种 XTS 并修改"（Platform 无删除接口，会在 mor-test 留下测试币种）；ACTIVE 改冻结字段 422、logo 不存在 422、
    被拒不写入照常验证（被拒请求不写库）。
  - 不跑 U（Platform 不可用，需改表名或停 Platform；本机已验证）。
  - root SQL 只允许 SELECT（AC-10 存量分类读取，库名 mor_client_master）。
  - 新增 L：ledger / billing / card 消费旧接口的回归——--since 之后日志中 "Currency snapshot refresh failed" 与 ERROR 计数、容器健康。
    这就是消费方实际行为（严格 JSON 8192 / 65536），不另造请求（lesson：冒烟与消费方一致）。
写入：A / K / S 会注册 3 个验收 Client（OA.client_register，与切片 1 mor-test 验收相同）、建申请、提交，并保存 1 个案件的分类——均为验收数据。
业务时间：本需求的参考数据没有生效时间；验收数据全部用执行时刻（无回溯时间），不受"业务时间 ≥ 配置生效时间"约束（runbook 写明）。
用法：python3 -I refdata_mortest.py --since <发布开始 UTC，如 2026-10-28T02:00:00Z> [--only P,R,C,F,A,K,S,L]
结果：verification/acceptance-mor-test-<UTC>.json。
"""
import argparse
import os
import re
import subprocess
import sys
import types
from pathlib import Path

sys.dont_write_bytecode = True
HERE = Path(__file__).resolve().parent
REQ = HERE.parents[1]
LOCAL = REQ / "verification/local-e2e/refdata_acceptance.py"
os.environ.setdefault("E2E_PROBE_IMAGE", "sha256:aec535863836669db6063a139c448ee817d00f64d6a4adea008448a413e4d5b5")
MOR = Path("/Users/kenny/Desktop/Workspace/Code-MoR")


def transformed_source():
    src = LOCAL.read_text(encoding="utf-8")
    src = src.replace('os.environ["E2E_TARGET"] = "local"', 'os.environ["E2E_TARGET"] = "test"', 1)
    # 1) 去掉 flip(...) + try/finally restore_flips() 子步骤，改记 SKIP（同缩进）
    pattern = re.compile(r'^(?P<ind>[ \t]*)flip\((?P<args>[^\n]*)\)\n(?P=ind)try:\n.*?\n(?P=ind)finally:\n(?P=ind)    restore_flips\(\)\n',
                         re.M | re.S)
    src, n = pattern.subn(lambda m: f'{m.group("ind")}skip("MORTEST", "临时停用参考数据 {m.group("args").replace(chr(34), "")}",'
                                    f' "mor-test 不做 root 写；停用场景见本机验收")\n', src)
    if n != 4:
        raise SystemExit(f"预期去掉 4 处临时停用，实际 {n}（本机验收脚本已变化，先核对）")
    # 2) 去掉 C 的 XTS 草稿币种（新建与修改）
    start = src.index('    if "XTS" not in items:')
    end = src.index('    ctx["xts"] = True\n') + len('    ctx["xts"] = True\n')
    src = src[:start] + '    skip("AC-13", "DRAFT 币种新建与修改（XTS）", "mor-test 不留下测试币种；本机已验证")\n' + src[end:]
    # 3) 结果文件与库名
    src = src.replace('f"acceptance-local-{', 'f"acceptance-mor-test-{').replace('"env": "local"', '"env": "mor-test"')
    src = src.replace('"mor_client_master_v2e2e"', '"mor_client_master"').replace('"mor_platform_v2e2e"', '"mor_platform"')
    src = src.replace('if __name__ == "__main__":\n    sys.exit(main())', '')
    return src


def load():
    module = types.ModuleType("refdata_acceptance_mortest")
    module.__file__ = str(LOCAL)
    exec(compile(transformed_source(), str(LOCAL), "exec"), module.__dict__)  # noqa: S102  本仓库脚本
    E = module.E

    def platform(path):
        status, text = E.private_call("platform", "GET", "/platform/v1" + path, {"Accept": "application/json"})
        import json
        return status, (json.loads(text) if text.strip().startswith(("{", "[")) else {"raw": text[:200]})

    def root_sql(db, statement):
        if not statement.lstrip().upper().startswith("SELECT"):
            raise module.Stop("mor-test 验收只允许只读 SQL：" + statement[:80])
        env = dict(line.split("=", 1) for line in (MOR / "test-environment/.env.test").read_text().splitlines()
                   if "=" in line and not line.startswith("#"))
        out = subprocess.run(["docker", "exec", "-i", "-e", f"MYSQL_PWD={env['MOR_MYSQL_ROOT_PASSWORD']}", "mor-test-mysql-1", "mysql", "-uroot",
                              "-N", "-B", "--default-character-set=utf8mb4", db], input=statement, capture_output=True, text=True)
        if out.returncode:
            raise module.Stop("SQL 失败：" + out.stderr[:300])
        return [r.split("\t") for r in out.stdout.splitlines() if r]

    module.platform, module.root_sql = platform, root_sql
    module.REQ = REQ
    return module


def consumers(module, since):
    record = module.record
    for service, marker in (("ledger-service", "Currency snapshot refresh failed"), ("billing-service", "Currency snapshot refresh failed"),
                            ("card-service", None)):
        logs = subprocess.run(["docker", "logs", "--since", since, f"mor-test-{service}-1"], capture_output=True, text=True)
        text = logs.stdout + logs.stderr
        errors = sum(1 for line in text.splitlines() if " ERROR " in line)
        failed = text.count(marker) if marker else 0
        health = subprocess.run(["docker", "inspect", f"mor-test-{service}-1", "--format", "{{.State.Health.Status}}"],
                                capture_output=True, text=True).stdout.strip()
        record("AC-02", f"{service}：{since} 之后 ERROR {errors}" + (f"、{marker} {failed}" if marker else "") + f"，健康 {health}",
               "PASS" if errors == 0 and failed == 0 and health == "healthy" else "FAIL")


def main():
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--since", required=True, help="发布开始时间（UTC ISO），用于消费方日志窗口")
    parser.add_argument("--only", default="P,R,C,F,A,K,S,L")
    args = parser.parse_args()
    module = load()
    selected = args.only.split(",")
    if "U" in selected:
        raise SystemExit("mor-test 不跑 U（Platform 不可用场景）")
    if "L" in selected:  # 先记消费方结果，main() 结束时一并写入结果文件
        consumers(module, args.since)
    rest = [s for s in selected if s != "L"]
    sys.argv = [sys.argv[0], "--only", ",".join(rest) or "R"]
    return module.main()


if __name__ == "__main__":
    sys.exit(main())

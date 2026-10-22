"""文件多存储并存第二段阶段 7 mor-test 验收清理（只处理本次运行生成的数据）。

能干净撤销的：
  · C12 关联的材料：用运行记录里的合成账号登录，经客户端接口 C13 删除（DELETE /onboarding/v1/applications/{app}/documents/{doc}），
    走与页面相同的业务规则；CCT 行随之删除。
保留为测试数据（与 2026-10-14 入驻验收的做法一致，release.md 第 7 节说明原因）：
  · 合成账号 fs2-<run>@example.test、其 Client / 租户与草稿申请：跨 Platform IAM、CCT 多张表，无业务删除接口；
  · 本次上传的 Platform 文件（source_system=client-console-bff）与 MinIO 对象：文件服务不提供删除，审计与扫描记录引用它们；
  · 审计事件（FILE_DOWNLOAD_AUTHORIZE 等）。
若用了 --submit：compliance 中的审核任务同样无法删除，只能由运营按正常流程退回或拒绝。
用法：python3 -I stage7_s2_cleanup.py <FS2_STATE/run-*.json> [--dry-run]
"""
import json
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import s2_common as c  # noqa: E402


def main():
    record = json.loads(Path(sys.argv[1]).read_text())
    dry_run = "--dry-run" in sys.argv
    report = {"run": record["run"], "dryRun": dry_run, "applicationId": record.get("applicationId"),
              "documentId": record.get("documentId"), "uploads": record.get("uploads")}
    if not record.get("documentId") or dry_run:
        print(json.dumps(report, ensure_ascii=False, indent=1))
        return
    client = c.af.Client()
    status, grant = client.call("/auth/v1/login", "POST", {"email": record["email"], "password": record["password"]}, auth=False)
    client.token = grant["accessToken"]
    status, body = c.client_call(client, f"/onboarding/v1/applications/{record['applicationId']}/documents/{record['documentId']}",
                                 "DELETE", key=True)
    report["documentDelete"] = status
    left = c.sql(f"SELECT COUNT(*) FROM mor_client_master.t_application_document WHERE document_id={int(record['documentId'])}")[0][0]
    report["documentRowsLeft"] = int(left)
    report["legacyRows"] = c.sql("SELECT COUNT(*) FROM mor_client_master.t_application_document WHERE create_time < '2026-10-22 15:22:00'")[0][0]
    print(json.dumps(report, ensure_ascii=False, indent=1))
    c.save(f"cleanup-{record['run']}.json", report)
    if status not in (200, 204) or int(left) != 0 or report["legacyRows"] != "52":
        raise SystemExit("清理未完成或旧材料行数不是 52，请人工核对")


if __name__ == "__main__":
    main()

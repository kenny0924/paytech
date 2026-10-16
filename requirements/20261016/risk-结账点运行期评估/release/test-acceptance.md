# mor-test 发布与验收记录（阶段 7，2026-10-17 UTC 20:05～20:09）

按 [发布清单](./release.md) 执行，D1～D4 按建议（激活结账默认放行基线、合成评估验收后清理、临时代理、不部署 Web）。执行前已通知并行使用 test 的 code-mor-76，结束后再次通知。

| 步骤 | 结果 |
|---|---|
| 备份 | `~/Code-MoR-backups/20261017-risk-checkout-runtime-test/20261016T200530Z/`（目录 0700、文件 0600、SHA256SUMS）：`mor_risk` 全库 dump、85 表行数、Risk inspect、16 个容器 image 清单 |
| 预检 | 85 表；快照 0 行；v4 结账变量 10；locale 引用 0；91018～91020、220001、(210001,100) 未占用 |
| 回归基线 | 临时代理 `nginx:1.30.4-alpine`（`127.0.0.1:28114` → `risk-service:18104`，Docker DNS）读取 27 个只读接口：支付与结账的创作上下文、流程/规则集/变量列表与详情、执行发布列表与支付基线详情、名单、5 条支付评估及解释，全部 200 |
| DDL | `db/ddl.sql`：85 表；新快照表 17 列 5 个索引 |
| DML | 空跑（事务内核对后 ROLLBACK）：v5 结账变量 12、91002 DISABLED、220001 FROZEN v5 version_no=100 hash `7f730e19…`；回滚后恢复原状。随后 COMMIT（20:06:27 UTC） |
| 换镜像 | 紧接 DML：`compose-candidate.yaml` → image `8e26b91b519c`，healthy（20:06:53，窗口 26 秒）；启动日志无 ERROR；Nacos 恰一个健康实例 172.30.73.25:18104 |
| 回归 | 27 个接口全部 200，部署前后响应字段结构**完全一致** |
| 结账创作 | 规则集创作上下文 v5：含 `line_count` / `INPUT_LINE_COUNT`，无 locale、无冷却/打标签；流程创作上下文无 `INPUT_LOCALE` |
| 门禁 | 以现有 v4 流程版本 `224761039558905856` 创建执行发布 → 400“决策流版本使用旧输入合同，请重新保存后发布”，未写发布 |
| 基线（D1） | 流程 `CHECKOUT_BASELINE_ALLOW`（开始 → 放行，flowVersion `236943227222069249`）；执行发布 `236943228086095872` 校验全部 PASSED → ACTIVE（TEST，引用 220001） |
| 评估（D2） | 订单 `991017000000000001`：ALLOW；同内容重放返回同一评估 ID；内容不一致 409。订单 `…002`（PARTNER + Local Partner）：ALLOW。PARTNER 无 Local Partner 400；`lineCount=0` 422（带字段）；合同 v4 400；19 位溢出订单 ID 422。按订单 ID 查询 200；解释输入 12 个字段、路径 start → allow |
| 日志 | 验收窗口 Risk 日志无输入原值（DIGITAL_GOODS、Client/Partner ID、金额键均 0 次）、无 ERROR |
| 清理 | [test-acceptance-cleanup.sql](./test-acceptance-cleanup.sql) 先 ROLLBACK 后 COMMIT：评估 2、流程轨迹 4、规则轨迹 0、Feature 0、快照 2；代理已删除 |
| 写入范围 | 与备份时行数比对，仅以下表变化且均为预期：流程 +1、流程版本 +1、节点 +2、连线 +1（基线）；执行发布 +1、发布审计 +15；点版本 +1（220001）；变量 +3。评估仍为 5 条支付评估 |

**现状：**test Risk = `8e26b91b519c`；结账点生效发布为默认放行基线。回退旧镜像 `727d51127e61` 需先回滚 DML，而结账执行发布已存在，按清单第 7 节已不可回退，只能在新镜像上修复。

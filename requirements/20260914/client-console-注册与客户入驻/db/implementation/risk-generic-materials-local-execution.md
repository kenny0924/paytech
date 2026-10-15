# Risk 通用材料程序：本机执行范围（待授权）

**状态：已执行（2026-10-14）。** 本机 `mor_risk` 见[本机全链路记录](../../verification/local-e2e-execution.md) A7（`@approved_by=236033080844881920`）；mor-test 见[发布记录](../../release/test-acceptance.md)（`@approved_by=221546852372267008`）。以下为当时批准的执行范围。依据见[通用化差异稿](../../generic-material-form-proposal.md#4-材料目录risk)。

- 精确目标：`127.0.0.1:13306/mor_risk`，仅写入 `t_dd_program`、`t_dd_program_version`、`t_dd_program_requirement` 三张既有表，不做 DDL。
- 执行文件：[risk-generic-materials-init.sql](./risk-generic-materials-init.sql)，由 [generate-risk-generic-materials.py](./generate-risk-generic-materials.py) 生成；`python3 -I generate-risk-generic-materials.py --check` 复核一致。
- 内容：3 个 Program（CLIENT_INDIVIDUAL_GENERIC / CLIENT_ORGANIZATION_GENERIC / RELATED_PERSON_GENERIC），各 1 个 ACTIVE 版本，共 10 条材料要求；ID 使用 9100000000000001 起的保留区间。
- 执行前：确认三个 program_code 与保留 ID 均不存在；在同一会话先 `SET @approved_by=<真实 Operations 审批账号 ID>`，未设置时 Risk 查询会返回不可用。
- `content_hash` 按 `CODE-MOR:RISK:DD_PROGRAM_VERSION:1` 计算；数组键名 `requirements/checks/screenings` 是对物理决策文档的实现解读，Risk 现行代码只校验格式。
- 回滚：按保留 ID 依次删除 requirement → version → program。
- 生效还需 Risk `onboarding.materials.enabled=true`；`application.yml` 已带三类映射，开关默认关闭。

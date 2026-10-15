# 交付记录：Operations 维护 Vault 数据类型与手动加解密

| 日期 | 阶段 | 内容 | 状态 |
|---|---|---|---|
| 2026-10-14 | 1 需求评审 | [requirement.md](./requirement.md) 评审稿：范围、不做项、D1～D6 决策点、AC-1～AC-11 | 待确认 |
| 2026-10-14 | 1 需求评审 | 用户说明手动加密用途：运营自行加密密钥并把 Token 写入业务表；D5 调整为“只写不读”，新增 AC-11 | 待确认其余决策点 |
| 2026-10-14 | 1 需求评审 | 用户要求密钥类也可人工解密，由功能权限控制；D4/D5/AC-11 改为“数据类型开关 + 普通敏感/密钥类两级权限点” | 待确认其余决策点 |
| 2026-10-14 | 1 需求评审 | 用户确认策略变更不需要审批，计入审计即可；D3、AC-2、AC-3 已更新 | 待确认 D1、D2、D6 |
| 2026-10-14 | 1 需求评审 | 用户回复“按建议”：D1、D2、D6 按建议确认；阶段 1 完成，进入阶段 2 | **已确认** |
| 2026-10-14 | 2 现状差异与影响 | [technique.md](./technique.md) 评审稿：差异表 11 项、逐系统影响 9 项、风险 7 项、工作量粗估 | 待确认 |
| 2026-10-14 | 2 现状差异与影响 | 用户回复“可以” | **已确认** |
| 2026-10-14 | 3 方案与设计 | technique.md 第 5～13 节；[Vault DDL](./db/vault/ddl.sql)；[Platform 注册 DML](./db/platform-support-service/registration-dml.sql)（174 行） | 待确认 |
| 2026-10-14 | 3 方案与设计 | 用户要求不做首次导入、直接从数据库加载，其余按方案：去掉 `VAULT_POLICY_FILE`；新增 [dml-local.sql](./db/vault/dml-local.sql)（9 行）、[dml-test.sql](./db/vault/dml-test.sql)（16 行）；热加载改为定时全量读取；测试夹具改为写表 | **已确认** |
| 2026-10-14 | 4 研发（Vault） | 经授权在本机 `mor_vault` 执行 ddl.sql 与 dml-local.sql（备份已留）；Vault Server 策略入库、定时全量刷新、11 个管理接口、人工加/解密与审计扩列；[管理接口 OpenAPI](../../../vault-service/api/openapi/admin-v1.yaml)；PDManer 生成器扩为六表；local/test compose 去掉策略文件、加入 `VAULT_ADMIN_CALLERS` | 进行中 |
| 2026-10-15 | 4 研发（Vault） | 补管理与人工加/解密入口测试；分页对齐 Java 公共分页（默认 10、最大 200）；Vault 完整门禁 `scripts/verify.sh` 13 项全部通过（含 Maven 客户端与黑盒）。下一步：Java `VaultAdminClient`、Operations BFF、Operations Web | 进行中 |
| 2026-10-15 | 4 研发（客户端/BFF/Web） | Java `VaultAdminClient`（默认关闭，新增 3 个错误码；业务 `VaultClient` 不变）及真实 Go Vault 黑盒往返；Operations BFF 13 个接口（路由决定数据级别、submissionId 作 Vault 幂等键、`/vault-admin` 只记元数据日志、Vault 错误码映射 403/404/409）；Web 6 个页面与敏感值组件（30 秒自动隐藏）、工单号组件。验证：客户端 40、黑盒 10、BFF 1286、Web 996 全部通过，Web 构建通过。未做：浏览器联调（阶段 5） | 待确认 |
| 2026-10-15 | 4 研发 | 用户回复“可以”：阶段 4 确认，授权阶段 5 | **已确认** |
| 2026-10-15 | 5 本机联调 | 重建本机 Docker Vault；`mor_platform_e2e` 执行注册 DML（174 行，试跑回滚核对后提交）与 [activation-local.sql](./db/platform-support-service/activation-local.sql)（13/11/6/1 行）；Operations BFF 新 jar 重启。经 Nginx 运营入口：[API 验收](./verification/local_acceptance.py) 27 项（AC-1～9、11）、[浏览器验收](./verification/local_browser.py) 19 项（AC-10）全部通过；临时撤销密钥类授权时密钥路由被 Gateway 403、普通路由不受影响，已恢复。发现并修正：本机 SQL 以 latin1 客户端执行导致中文乱码（4 张表注释、11 个菜单名、初始化理由），就地修正并在 SQL 文件头注明 utf8mb4；“操作人”列加宽以显示完整账号 ID。测试数据已清理，`mor_vault` 回到初始化数据 | 待确认 |
| 2026-10-15 | 5 本机联调 | 按用户试用反馈调整页面：删除数据类型页顶部提示；手动加密完成后不再换页，Token 显示在表单下方并可复制；手动解密结果并入同一卡片（遮罩、显示/隐藏、复制、清除、30 秒倒计时）。Web 测试 996、浏览器验收 19 项通过 | 已完成 |
| 2026-10-15 | 5 本机联调 | 用户确认审计只在 Vault 本地、暂不转发 Platform 集中审计，写入决定记录第 7 条；用户回复“提交吧”，阶段 5 确认并提交（未推送）。下一步：mor-test 执行 DDL/DML、注册激活与部署（阶段 6/7，需另行授权） | **已确认** |

当前实现与部署状态只见 [CURRENT_IMPLEMENTATION.md](../../../agent-docs/CURRENT_IMPLEMENTATION.md)。

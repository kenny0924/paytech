# payout S1 · 阶段 7 mor-test 发布与验收报告（2026-10-30）

> 用户授权：“U-1—U-6 按建议，进入阶段 7”；验收范围：“先只验运营侧，Client 部分留作遗留”。执行者：主会话。

## 1. 发布（14:01—14:08Z，按 [runbook](./runbook.md)）

| 步 | 结果 |
|---|---|
| 0 复核 | main `9eab368c`；`source_drift_check.sh` NO DRIFT（247 个文件）；7 个现网镜像 ID 与 baseline 一致；`env_check.sh` 回滚 5 个逐键相同、候选只差 Platform `SPRING_APPLICATION_JSON`；REQ-06、明细账运维会话确认无并行发布 |
| 1 备份 | `~/Code-MoR-backups/payout-s1-stage7/mortest-20261030T140111Z/`：`mor_platform`、`mor_vault`（均 `Dump completed`）、`env.test.before` |
| 2 预检 | 与阶段 6 读数一致：库 / 账号不存在；Vault 版本 21、880—885 空闲；块 147 全 0、ACTIVE 路由 652、max_block 146；语言包计划可执行（基线 #28） |
| 3 库与账号 | `.env.test` 追加 `MOR_PAYOUT_DB_PASSWORD`（未运行 prepare.py，避免改动其他生成文件）；`sql/10` 建库与账号，`sql/11` 核对：授权两行、0 表 |
| 4 DDL | 3 表，31 / 13 / 15 列，索引 5 / 1 / 3，0 行，无 FK / CHECK |
| 5 Vault | 数据类型 2、规则 3、策略版本 22（最新）；未重启 vault-service |
| 6 块 147 休眠 | permission 8、admin grant 5、client grant 390、resource 7、route 19、header 71、query 19、guard 114、template 4、content 8、template route 4；ACTIVE 路由仍 652 |
| 7—11 容器 | account `8dfd1060` → Platform `0ea9cc2d`（`notification.http.callers.payout-service` 四模板）→ 新 payout `75a6337c`（172.30.73.44，启动 4.7 s，内存 249 / 448 MiB）+ XXL 分组 19 / 任务 29（触发 200）→ ops-bff `626a74a7`、client-bff `24d3aa05` → ops-web `acb33e94`；全部 healthy，日志无 ERROR |
| 12 语言包 | 运营 #29：新命名空间 payout，新增 223 键，82 项核对通过 |
| 13 激活 | 块 147 资源 / 路由 ACTIVE；ACTIVE 路由 671（+19）；未登录访问新路径 401、未知路径 404（Gateway 已加载） |

## 2. 验收（运营侧，`payout_acceptance.py --ops-only`）

| 子命令 | 结果 |
|---|---|
| accounts | 5 / 0：maker / checker / ro / noperm 四个合成运营账号（Maker ≠ Checker） |
| api | **64 / 0**：运营代 Owner 提交（B1）字段清单与校验（AC-01）、业务幂等与 409 含已有 ID（AC-02 / AC-10）、跨 Owner 共用账号与相同 STABLE Token（AC-02）、库内无原值与 Vault 两类 Token、Reveal 与审计（AC-07）、Maker / Checker 四眼与冷却 24 h（AC-03）、拒绝与给 Client 的说明、停用 / 恢复重核 / 运营关闭（AC-06）、过期版本 409（AC-10）、运营余额视图 A / B / Z / C（AC-04 / AC-08）、ro / noperm 权限分支（R10）、他方回归（REQ-04、联合查询、多语言） |
| notify | **9 / 0**（AC-09 运营侧）：补偿任务全部处理完成；Platform 受理四类模板；投递数 = Client A 持 MANAGE 成员数；全部到终态（所有者地址被 SES 沙箱拒绝为 FAILED，账户状态不受影响）；参数无原值；投递地址为 Vault Token |
| check | **6 / 0**：事件序号连续、无 ERROR / FAILED 补偿事件、三表无原值、各服务日志无原值且 ERROR 行 0 |
| 浏览器 | zh-CN / en × 1440 / 390 共 56 步通过：O1—O5（含 Maker 通过 → Checker 批准写入）、四眼禁用、拒绝弹窗、Owner 余额页、无权限分支；无原始 i18n key、无页面横向滚动、无 HTTP 错误。en-390 首轮因脚本登录等待偶发停在登录页（无 HTTP 错误），单独重跑通过（`browser-run-en390b.log`） |

验收脚本修正：`api` / `api_ops` 收集 Reveal 原值时误含 SWIFT BIC（设计上以明文存于 `bank_code`，`raw_values()` 本已排除），导致 `check` 误报；已改为只收集户名 / IBAN / 账号。新增 `--ops-only`（运营代 Owner 提交替代 Client 入口）。

## 3. 遗留（用户决定留作遗留）

- **L-C1 Client 侧验收**：Client 门户接口（`/api/client/payout/v1/**`）提交、列表、详情、设默认（AC-05）、Client 自身余额页（AC-04）、TENANT_VIEWER 403 分支、Gateway 同键重放（AC-10），需 A / B 所有者凭据（`clients.json`）后执行 `payout_acceptance.py prepare/api`（不带 `--ops-only`）。
- **L-C2 AC-09 收件**：经 SES 模拟器成员确认 SENT / DELIVERED，依赖 L-C1 的 `prepare`。
- 验收合成数据清理草稿 `acceptance/cleanup-20261030T142616Z.sql`（7 个账户，未执行）；四个合成运营账号待停用。

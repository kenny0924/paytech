# REQ-03 risk-decision 承保记录 · 阶段 6 发布准备报告（2026-10-29 21:30—22:20 +04）

> 范围：只做发布准备。mor-test 上**只做了只读操作**：`docker inspect / ps / exec sha256sum / cp`（只读出）、root 只读会话 SELECT、`mysqldump --single-transaction` 导出到本机 `~/Code-MoR-backups/rq03-20261029/`（0600，不入库）。没有停 / 起容器，没有执行 SQL / DDL / GRANT，没有登录运营 API，也没有调用 Platform / IAM / i18n / data-query 写接口。镜像只在本机 docker 构建。未提交、未推送。**阶段 7 须用户明确授权后由主会话执行。**
> 材料：[baseline.md](./baseline.md)（现网读数）、[runbook.md](./runbook.md)（执行顺序、检查、回滚、验收）、`sql/`、`compose-*.yaml`、`harness/`、`scripts/`、`acceptance/`、`i18n-bundles/`、`precheck/`、`evidence/`。

## 1. 结论

| 项 | 结果 |
|---|---|
| 基线 | 三个服务的现网镜像都在 `e3e0c12f` 之内。risk-decision 1384 项 0 差异；ops-bff 只有 REQ-12 给 client-master-api 加 JavaDoc 造成的 16 个类行号表差异，字节码 0 差异；ops-web 256 个文件逐字节相同。没有现网独有内容 |
| 候选 | 干净 worktree `Code-MoR-rq03-rel` = `e3e0c12f` + 本需求 180 个文件（`evidence/rq03-source-files.sha256`，与研发 worktree 逐字节相同，漂移检查通过）。构建出的两个 JAR 与阶段 5 本机验证并在运行的 JAR **条目完全相同**（含 U1 列表口径修正）。ops-web：`tsc --noEmit` 通过，`npm test` **1617 / 1617** |
| 镜像 | 4 个，tag `rq03-20261029a`，钉 sha256（第 2 节；Platform 为增补，见第 8 节）；镜像内 JAR / dist 与构建产物相同；health-probe 与现网相同；nginx.conf 与现网相同；Entrypoint / User / Healthcheck / 端口 / WorkDir 与现网镜像相同（`evidence/image-verify.txt`） |
| env | 候选与回滚 8 个覆盖文件渲染后，与运行容器**逐键逐值相同**（risk 33、ops-bff 20、ops-web 7、Platform 59）。**不新增 env / 配置**：只记元数据的前缀在镜像内 `application.yml` |
| mor_risk_decision | 预检：三张新表、同名索引、过程都不存在，现有 54 张表，应用账号的库级 DML 覆盖新表。离线演练（mor-test 只读导出 + 断网 mysql:8.4.11）：前滚 0.24 s，可重跑；回滚在**有承保记录时 SIGNAL 拒绝**，删去夹具记录后回滚成功，库与导出**逐行相同**；回滚后可再次前滚 |
| 注册块 143 | 预检：七表区间、编码、路径都未占用，依赖都在。离线用现网 Platform JAR 的生产编译器编译 Gateway 快照：基线 effective **619** → 休眠 619（loaded 637）→ 激活 **631（+12）** → 重复激活 631 → 停用 619 → 删除 619，删除后 mor_platform 与导出逐行相同；重复插入被主键拒绝 |
| data-query | `MOR_RISK_DECISION_REPLICA` 与 `GOLIVE_UNDERWRITING_APPROVED` 都未占用；账号 `data_query_demo_reader` 目前没有 mor_risk_decision 权限。准备了表级 GRANT / REVOKE 和 API 登记脚本；`plan` 已在 mor-test 只读跑过，2 项未满足（缺授权、缺表），属发布前的预期 |
| 语言包 | operations-console 现网 **#22**。计划 #23：新增 247（risk 242、nav 3、client-review 1、master-data 1），基准变更、废弃、恢复都是 0，zh-CN 导入 247，不新建命名空间，`zhChangedNotImported` 0。只读干跑通过 |
| 角色 | 运营侧只有 `PLATFORM_ADMIN` / `OPERATIONS_VIEWER` 两个系统角色，没有业务风控角色。建议本次不给常设角色授权（第 5 节） |
| 验收 | mor-test 没有经新流程批准的 Client（37 个 APPROVED 都是 LEGACY 迁移，CRR 为空），所以 LOW / HIGH 路径要先批准两个在途测试案件（Q-1）。脚本已备好：`accounts` / `prepare` / `api` / `check`，并已做语法检查。mor-test 上只能在阶段 7 实跑 |

## 2. 候选镜像

| 服务 | 候选（`rq03-20261029a`） | 内容 | 回滚（现网） |
|---|---|---|---|
| risk-decision-service | `sha256:8934f65a6cf85a9b7e42df8abb77208acbacee2b74e2a878b553686cfa114cd3`（JAR `73570443ba972ea1`） | 现网 + 承保 88 个新增条目、5 个改动（`application.yml`、错误映射、MapperScan、api pom） | `sha256:288b0ff6…`（relsnap-20261025a） |
| operations-console-bff | `sha256:4f08f1e601beb791f8e61c088e471e8839fc65783acbf513e412e5fcaf200b6b`（JAR `1646cf7af4c87a13`） | 现网 + 承保 53 个新增条目、31 个改动（其中 16 个为 client-master-api 行号表） | `sha256:6833f484…`（wfdq-20261029a） |
| operations-console-web | `sha256:2f7722daa531a012a8a1096a20c6b653f21b17ee6c018b2a703f9fa276bf0002`（dist 259 个文件） | 现网 + 风控 · 承保页面、Client 审核 / 详情入口 | `sha256:0b456b8a…`（rq12-20261029a） |
| platform-support-service | `sha256:49f710118adb1d074de4bb4aa711e88cebec1544f3bfb00db7b4b12f213329d9`（JAR `6d7ab1069d63938b`） | 现网 + `console-resource-capabilities.json` 加 1 个页面能力（3122 项中只有这 1 项不同，`evidence/jar-candidate-platform-vs-live.txt`） | `sha256:7671724…`（wfdq-20261029a，JAR `0345c0dd`） |

对比明细：`evidence/jar-candidate-{risk,opsbff}-vs-live.txt`。

## 3. 发布顺序（详见 runbook 第 3 节）

① 复核与通知 → ② 备份 → ③ 只读预检 → ④ mor_risk_decision DDL + DML → ⑤ risk-decision → ⑥ ops-bff → ⑥b Platform → ⑦ 块 143 休眠 → 编译核对 → 激活 → ⑧ ops-web → ⑨ 语言包 #23 → ⑩ GRANT + 数据源 + 模板 → ⑪ 角色（默认不动）→ ⑫ 验收（账号 → 批准 L / H → API → 只读核对 → 浏览器）。每步都有检查项和回滚编号（runbook 第 4 节，R10 → R4 逆序）。

**影响窗口**：⑤ risk-decision 重启约 30—60 秒，结账风控评估、风控配置页、出款资格检查在此期间不可用（mor-test 没有真实交易；需通知并行会话）。

## 4. data-query（runbook ⑩）

- DBA 授权（`sql/40-mor-test-data-query-grant.sql`）：`GRANT SELECT ON mor_risk_decision.t_client_underwriting TO 'data_query_demo_reader'@'%';`。用表级而不是库级，因为同库还有决策配置和评估输入，模板只读这一张表（S-10）。表级授权要求表已存在，所以放在 ④ 之后。回收用 `sql/41-…-revoke.sql`。
- 登记（`acceptance/rq03_data_query.py apply --yes`）：先做连接测试（不落库），再建数据源（连接参数 `{"connectTimeout":"3000","connectionTimeZone":"UTC"}`、连接池 3，同 `MOR_COMMERCIAL_REPLICA`），然后对 4 个 Client 试跑（不落库），最后建模板。模板参数 `clientId INTEGER` 必填，`maxRows 1`、超时 5 秒、不分页，出参 6 列都不屏蔽。口令读 `test-environment/private/data-query-demo.env`，不打印。

## 5. 角色组合（runbook ⑪，不执行）

现状：父菜单"风控管理"要求 `RISK_DECISION_CONFIG_READ`，只有 `PLATFORM_ADMIN` 和 `OPERATIONS_VIEWER` 持有，其余风控权限都只在 `PLATFORM_ADMIN`。块 143 激活后，`PLATFORM_ADMIN` 拥有承保 READ / MANAGE / APPROVE。

建议（Q-3 默认）：**本次不给任何常设角色授权**。
- mor-test 没有业务风控角色，平台管理员足够演示。
- 四眼验收用 ⑫ 的合成账号。
- `OPERATIONS_VIEWER` 是否要能看承保（会看到 Client 法定名称、保证金决定）属于业务决定，不在本需求默认范围。

如果用户决定建"风控承保员 / 风控负责人"常设角色，经运营 API（管理员令牌，`Idempotency-Key`）调用：

```
POST /api/operations/iam/v1/roles
{"roleCode":"RISK_UNDERWRITER","roleName":"风控承保员","permissionIds":["900000000000000536","900000000000143101","900000000000143102"],"reason":"REQ-03 承保：常设角色"}
POST /api/operations/iam/v1/roles
{"roleCode":"RISK_UNDERWRITING_LEAD","roleName":"风控负责人","permissionIds":["900000000000000536","900000000000143101","900000000000143102","900000000000143103"],"reason":"REQ-03 承保：常设角色"}
```

把角色分给账号用现有账号编辑界面完成。回滚 `92-…` 时会一并删除这些角色对 1431xx 权限的授权行。

## 6. 待决（阶段 7 前请用户确认）

| # | 问题 | 建议 |
|---|---|---|
| Q-1 | mor-test 验收要经运营 API 批准两个在途测试入驻案件：L = 切片 1 验收停在四眼前的 "Acceptance Trading 1027040243 LLC"；H = 参考数据冒烟 "Refdata AE 1028040509 LLC"，定为 MCC 5967 + CRR HIGH。副作用是两个 Client 变为 ACTIVE 并投影 CLIENT 资格。若不同意，只能用 LEGACY Client（CRR 为空 → MEDIUM），LOW 自动承保和 HIGH 四眼在 mor-test 无法验证，只有本机证据 | 同意（都是验收合成数据；四眼由切片 1 合成复核人完成） |
| Q-2 | 新菜单"承保"sort 90 与 relsnap 加入的"资格登记"`OPERATIONS_QUALIFICATIONS` 相同，同序时按 ID 排，"承保"会排在最后，实际不影响使用 | 保持（本地 e2e 已按此显示）；如需固定次序，阶段 7 前把块 143 的 sort 改为 100（需要同步修改 initial-data、需求目录 SQL、本机库，并重做演练） |
| Q-3 | 常设角色授权 | 本次不授权（第 5 节） |
| Q-4 | 规则初值 USD 50,000、排除清单为空（O-1，待风控确认）；H 的月流水币种是 AED，规则没有 AED 阈值，因此 `CURRENCY_CONFIGURED` 不通过 | 按初值发布；风控给正式值后另发规则版本 DML |
| Q-5 | 验收产生的承保记录是审计数据，回滚 SQL 有记录时会拒绝 | 回滚时只回退镜像，保留表（runbook R4） |
| Q-7 | 是否在本次一并发布 Platform（只差页面能力清单，第 8 节）。不发布时功能、导航、权限、路由都正常，只是菜单资源管理页会把"承保"菜单和两个按钮标为不支持、不能在页面上编辑（可等下一次 Platform 发布顺带） | 一并发布（与 REQ-05 / relsnap 的做法一致；代价是 Platform 重启约 60 秒） |
| Q-6 | **阶段 7 需要用户明确授权**：停 / 起 4 个容器、root DDL / DML / GRANT、注册块、语言包 #23、data-query 登记、IAM 合成账号、验收写数据 | — |

## 7. 本机资源与清理

- 发布 worktree `/Users/kenny/Desktop/Workspace/Code-MoR-rq03-rel`（882 MiB，含 node_modules / target），保留到阶段 7 结束，用于漂移后重建。
- 本机备份 `~/Code-MoR-backups/rq03-20261029/`（468 MiB：现网 JAR / dist、候选 JAR、两份只读导出，0600 / 0700）。
- 中间文件已清（镜像构建上下文、演练 canon 文件、演练容器）。当前可用磁盘约 13 GiB。
- 构建时有一次 zsh 把 `$svc:r…` 解析成修饰符，产生了两个错误 tag `mor-test/<svc>q03-20261029a:latest`。已改为正确 tag 并删除错误 tag，镜像 ID 不受影响。

## 8. 增补：页面能力清单（2026-10-29 22:25 +04）

tax 会话发现：块 143 的菜单与按钮写进了 `platform-support-initial-data.sql`，但没有写进 Platform 的 `console-resource-capabilities.json`。

- **这个文件做什么**：Platform 的 `ConsoleResourceCapabilities` 在启动时从 classpath 加载它，作为"当前部署代码支持的页面组件和按钮"清单，供菜单资源管理（`/console-admin` 的列表、详情、新建、修改、启停）使用。`ConsoleResourcePolicy.supported()` 判断某个 MENU 的 componentKey 与路由、某个 BUTTON 的编码是否在清单里：不在清单里的会被标为不支持，`validate()` 会拒绝对它的新建或修改（"不支持的页面组件或按钮操作"）。它不影响导航菜单下发、权限判断和 Gateway 路由。
- **先例**：relsnap（块 137）的 `OPERATIONS_QUALIFICATIONS`、workflow（块 127）、财务页面都同时加了这里。Web 的 `src/navigation/pageCapabilities.ts` 是同一份清单的镜像，rq03-web 已经加了 `OPERATIONS_RISK_UNDERWRITING`；对比后发现两边只差这一项。Web 有 `financeWorkflow.test.ts` 只比对财务页面，Platform 没有测试比对清单和已注册的资源。
- **处理**：在研发 worktree 和发布 worktree 的 Platform 清单里，在 `OPERATIONS_RISK_EVALUATIONS` 后面加入 `OPERATIONS_RISK_UNDERWRITING`（"承保"，`/risk/underwriting`，按钮 `…_MANAGE` "维护"、`…_APPROVE` "复核"），内容和位置都与 Web 镜像一致。研发源码清单变为 181 个文件，漂移检查通过。
- **验证**：Platform `OperationsRoleManagementTest` 11 / 11（启动完整上下文，清单加载时的重复项 fail-fast 通过）；从干净来源构建 Platform 后，与现网 JAR 对比，3122 项中只有这 1 项不同；镜像 `49f71011` 核对通过（JAR、health-probe、元数据）；env 59 键逐值相同。
- **发布**：runbook 新增 ⑥b（ops-bff 之后、块 143 之前）与回滚 R6b，是否本次发布见 Q-7。ops-bff、ops-web、risk-decision 不包含这个文件，镜像不变。
- **其他缺口（与本需求无关，未处理）**：清单里还缺 `OPERATIONS_EXTERNAL_INTEGRATIONS`（含按钮 `…_MANAGE`）以及 `OPERATIONS_RISK_TEST_CANDIDATE_CREATE / _VALIDATE` 两个按钮。目录型菜单（componentKey 为空）本来就不需要登记。


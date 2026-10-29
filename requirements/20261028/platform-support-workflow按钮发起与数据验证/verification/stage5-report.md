# REQ-05 阶段 5 · 本机联调报告（后端与 API，2026-10-29 11:22—11:35 +04）

> 只操作本机：`local-environment` MySQL 13306（`mor_platform_v2e2e`、`mor_commercial_v2e2e` 只读、新建一个本机只读账号）、本机 Platform / ops-bff JAR 进程、本机 Gateway 快照。未碰 mor-test，未提交、未推送。**新 JAR 保持运行。** 浏览器走查由 wfdq-web 另行进行（18311 不由本报告操作）。
> 方法同 [REQ-01 阶段 5](../../commercial-开通项前置门禁/verification/stage5-report.md)：真实令牌经 Nginx 18444 → Gateway → ops-bff → Platform。脚本、备份、日志在 [stage5/](./stage5/)。

## 1. 结论

| 项 | 结果 |
|---|---|
| `mor_platform_v2e2e` 前滚 DDL | 预检 0 行；执行成功，RUNNING 且有业务键的 14 个实例已回填槽位，唯一索引与 5 个检查列就位 |
| 注册块 142（3 条路由）插入 → 激活 | **真实 Gateway 编译通过**：插入后（DISABLED）快照照常刷新（routeCount 510），激活后刷新成功 routeCount 513，之后持续每 5 秒成功 |
| Platform / ops-bff 换 worktree JAR | 两者 readiness UP（Platform 10 秒、ops-bff 4 秒）；新进程无 ERROR |
| API 联调 `stage5/stage5_e2e.py` | **38 / 38 通过**（明细 `stage5/stage5-e2e-result.json`、`stage5/logs/10-e2e.log`） |
| XXL 定时重查 `workflowDataQueryRecheck` | **本机未执行**：本机没有 XXL 管理端（只有 mor-test 的 `mor-test-xxl-job-admin-1`，不在授权范围），本机 Platform 也未配置 XXL 执行器。重查路径由 auto 测试 `WorkflowDataQueryRecheckJobHandlerTest`（真实本机 MySQL / Redis，3 例）覆盖；本机联调覆盖首查（补偿任务）与立即检查两条触发 |

## 2. 环境变更清单

| 时间（+04） | 动作 | 备份 / 回滚 |
|---|---|---|
| 11:22:38 | mysqldump `mor_platform_v2e2e` 工作流三表、Gateway 四表 | `stage5/backup/mor_platform_v2e2e.workflow-tables.20261029.sql`、`…gateway-routes.20261029.sql` |
| 11:23:25 | 前滚 `paytech/platform-support/docs/database/sql/changes/2026-10-29-workflow-data-query.sql`（日志 `logs/02`） | `…-rollback.sql`（新代码停止后） |
| 11:23:52 | 作废 e2e 库唯一旧形状 DATA_QUERY 模板 `WF_E2E_DQ_093951`（只被未发布草稿 `WF_E2E_BAD_093951` 引用）：配置改为新形状占位并 INACTIVE（`stage5/30-legacy-data-query-template.sql`） | `30-legacy-data-query-template-rollback.sql` |
| 11:24:49 | 注册块 142 插入（`db/registration-142.sql`，38 行，DISABLED） | `db/registration-142-rollback.sql` |
| 11:25:10 | 块 142 激活（`db/registration-142-activate.sql`） | 回滚脚本含删除；或改回 DISABLED |
| 11:26:33 | JAR 备份 `cp -p` | `local-environment/e2e/jars/paytech-platform-support-app-exec.jar.pre-wfdq`、`paytech-operations-console-bff-app-exec.jar.pre-wfdq` |
| 11:26:33—11:26:55 | 停 Platform（pid 39556）→ `.tmp` + `mv -f` 安装 → 按原 argv / 环境 / 工作目录（含 SPRING_APPLICATION_JSON、RabbitMQ）重启 | 新 pid **92711**；`local-environment/e2e/platform.pid` 已更新 |
| 11:27:13—11:27:28 | 停 ops-bff（pid 99123）→ 同法安装重启 | 新 pid **92955** |
| 11:29:26 | **本机专用**：启用块 131 的 3 项授权（平台管理员角色：DATA_QUERY_READ / DATA_SOURCE_MANAGE / DATA_QUERY_TEMPLATE_MANAGE，原 REVOKED；DATA_QUERY_EXECUTE 未动）与 7 条 data-query 管理路由（原 DISABLED），用于经运营 API 登记数据源与模板（`stage5/40-data-query-admin-local.sql`）。快照刷新成功 routeCount 520 | `40-data-query-admin-local-rollback.sql` |
| 11:29:47 | **本机专用**：MySQL 只读账号 `golive_e2e_reader`@`%`，仅 `mor_commercial_v2e2e` SELECT（相当于 mor-test 的 MOR_COMMERCIAL_REPLICA；`stage5/50-commercial-reader-local.sql`，口令只在会话暂存区） | `DROP USER IF EXISTS 'golive_e2e_reader'@'%';` 并停用 / 删除数据源 |
| 11:33—11:35 | `stage5_e2e.py` 经运营 API：数据源 2、查询模板 4、动作模板 5、流程定义 2、实例 7；IAM 建 2 个受限角色与账号（口令只在暂存区） | 见第 3、6 节 |

**对其他本机服务的影响（card，已处理：用户授权后于 11:40—11:41 替换 card JAR，见下方"card 替换"）**：本机 `local-environment/e2e/jars/paytech-card-app-exec.jar`（10-27 23:56，与 `.pre-refclient-swap` 逐字节相同，内含 `PlatformCountryRegionClientImpl`）早于 refclient e2185c1b，仍调用 Platform 已在 e2185c1b 删除的 `CountryRegionQueryApi`（`GET /platform/v1/country-regions`）。最初按授权范围未替换 card JAR；以下为替换前的影响分析。

- 调用路径（e2185c1b^ 源码）：仅 BIN 库导入一条——补偿任务 `CardBinLibraryImportHandler.handle` 在导入文件扫描可用后调用 `PlatformCountryRegionClient.listCountryRegions()` → `PlatformCountryRegionClientImpl` → `CountryRegionQueryApi.listCountryRegions()`，用于 CSV 国家 → 区域核对。卡 BIN 查询与 Card 其他接口不调用。
- 预期症状：运营上传 BIN 库文件后，导入版本停在 VALIDATING；card 日志出现对 `/platform/v1/country-regions` 的 404（`HttpClientErrorException.NotFound`）。该异常属未分类异常，补偿任务按"结果未知"有界重试，耗尽后补偿失败，版本既不接受也不拒绝。不上传 BIN 库则无可见影响；card 启动与健康检查不受影响（HTTP 接口代理按调用发请求）。
- 修复：换上 refclient 时期或更新的 card JAR（改用 Platform 参考数据客户端，与 mor-test 一致）；或停新 Platform、按第 7 节第 1 步从 `paytech-platform-support-app-exec.jar.pre-wfdq` 还原（同时撤回本需求的 Platform 改动）。修复后，卡住的导入版本由补偿任务重试或重新上传处理。

其他消费者（acquiring、gateway、client-console-bff、billing、ledger、client-master、commercial）不调用被删接口。

**card 替换（用户授权，2026-10-29）**

- 替换前核对（e2185c1b^ → 8d24df8a 的 `paytech/card`）：只有 8a441c90（BIN 号段列表查询，现有表上的新 SELECT）与 e2185c1b（改用参考数据客户端）两次提交，**无 card DDL / 数据变更 SQL、无新环境变量**。`application.yml` 新增 `platform-support.reference-data.client.kinds: [COUNTRY]`（不配 base-url，经 Nacos 发现 `platform-support-service`），readiness 组加入 `referenceData`（首次加载国家快照前不就绪）。新出站只有 `GET /platform-support/platform/v1/reference-data/countries`；`mor_platform_v2e2e.t_ref_country` 已有 249 行。
- 构建：worktree `paytech` 反应堆 `-pl card/paytech-card-app -am package -DskipTests`，离线、私有仓库 `~/.m3-wfdq`（日志 `stage5/logs/11-card-build.log`）。新 JAR 含 `paytech-platform-support-reference-data-client`，不含 `PlatformCountryRegionClientImpl`。

| 时间（+04） | 动作 | 备份 / 回滚 |
|---|---|---|
| 11:40:55 | 备份 `cp -p`；停 card（pid 39828） | `local-environment/e2e/jars/paytech-card-app-exec.jar.pre-wfdq`（与 `.pre-refclient-swap` 相同） |
| 11:41:08 | `.tmp` + `mv -f` 安装；按原 argv / 环境 / 工作目录重启（日志追加 `local-environment/logs/card.log`；`e2e/card.pid` 已更新） | 新 pid **98991**；日志 `stage5/logs/12-card-swap.log` |
| 11:41:14 | readiness（`127.0.0.1:19111/actuator/health/readiness`，含 `referenceData`）UP，启动 5.2 秒，无 ERROR | — |

- 验证：Platform（pid 92711）11:41:13 收到 card-service 内部调用并正常完成（DUAL 模式明文 `X-Caller-Service: card-service`），card readiness 因此转为 UP；以 card 的内部调用方身份直接请求 `GET /platform-support/platform/v1/reference-data/countries` → 200、249 条；旧接口 `/platform/v1/country-regions` → 404（已无调用方）。未触发 BIN 库导入等写操作。
- 已知无关告警：card 的 XXL 执行器注册连接拒绝（本机无 XXL 管理端，替换前同样存在）。

## 3. 演示数据（给浏览器走查）

登录同 REQ-01（`local-environment/e2e/secrets.env` 的 `E2E_ADMIN_EMAIL` / `E2E_ADMIN_PASSWORD`；执行人 / 跟进人均为该管理员）。

| 对象 | 编码 / ID |
|---|---|
| 数据源 | `GOLIVE_COMMERCIAL_LOCAL` `241464383090200576`（mor_commercial_v2e2e，只读账号）；`FIXTURE_REQ05` `241464534647181312`（mor_data_query_fixture） |
| 查询模板 | `GOLIVE_AGREEMENT_DRAFTED`（clientId，声明 6 列）、`GOLIVE_AGREEMENT_ACTIVE`（agreementId）、`GOLIVE_COLLECTION_CAPABILITIES_ENABLED`（agreementId）、`E2E_REQ05_SLOW`（1 秒必超时） |
| 动作模板 | `REQ05_GOLIVE_DRAFTED`（输出 agreement_id TEXT、business_mode ENUM MOR/PSP、capability_codes TEXT）、`REQ05_GOLIVE_ACTIVE`、`REQ05_GOLIVE_COLLECTION`（pending_capabilities TEXT）、`REQ05_SLOW_CHECK`、`REQ05_PSP_CONFIRM`（MANUAL_CONFIRM） |
| 流程定义 | `ACC_REQ05_GOLIVE_COMMERCIAL` `241464540657618944`：启动表单 clientId → N3 起草检查 → 排他网关（`drafted.business_mode` = MOR → N4 生效检查 → N8 收款开通检查 → 结束；默认 → PSP 人工复核 → 结束）；`ACC_REQ05_CHECK_FAILURE` `241464541530034176`：单个慢检查节点 |

| 实例 | 业务关联键 | 状态 | 动作与检查状态（动作运行 ID） |
|---|---|---|---|
| `241464542310174720` | `CLIENT:237161687952666625` | COMPLETED | drafted / active / collection 全部系统完成（SATISFIED，提交人为空 → 活动显示"系统"）：级联完成示例 |
| `241464542679273472` | `CLIENT:240674482170114049` | RUNNING | N3、N4 系统完成；**N8 collection `241464543144841217` PENDING · 尚未满足 · missing `CAPABILITY_NOT_ENABLED`**（已立即检查过一次）；重复发起返回本实例 |
| `241464542872211456` | `CLIENT:240674874987655169` | RUNNING | **N3 drafted `241464542872211459` PENDING · 尚未满足 · missing `AGREEMENT_NOT_DRAFTED`** |
| `241464543132258304` | `CLIENT:900000000001380201` | RUNNING | N3 系统完成（business_mode PSP）→ 网关默认边 → **pspConfirm `241464543425859587` 人工确认待办**（非 DATA_QUERY 对照） |
| `241464545422348288` | `E2E:REQ05:FAILED` | RUNNING | **slow `241464545422348291` PENDING · 检查失败 · `QUERY_TIMEOUT` · 连续失败 2 · 无成功检查** |
| `241464555799056384` | `E2E:REQ05:TERMINATE` | TERMINATED | slow `241464555799056387` CANCELLED（检查进行中终止，结果丢弃） |

"检查中"（首查尚未完成）是瞬时状态，没有常驻数据；按编码发起 `ACC_REQ05_CHECK_FAILURE`（任意新业务关联键）后约 1 秒内可见。
注意：每次进入页面若对失败 / 未满足的动作点"立即检查"，会实际执行查询并更新最近检查时间与失败计数。

i18n：本阶段未发布本机语言包（新增 key 属 ops-web，由 wfdq-web 在浏览器走查前按其流程处理）。

## 4. API 联调明细（38 项，全部通过）

| 组 | 用例 |
|---|---|
| 准备 | 数据源 2 个 ACTIVE；查询模板 4 个 |
| 模板选择 / 配置校验 | 列表 3 条 GOLIVE_*，含 `versionNo=1`、`timeoutSeconds=5`、参数声明；校验：合法 → `[]`；参数未声明 / 必填缺失 / 输出列未声明 → 问题码与 itemKey（`agreementId`、`clientId`、`client_name`）；模板不存在 → itemKey null；新建引用不存在模板 → 400 |
| 定义 | 两个草稿无问题，均发布成功（DATA_QUERY 可发布） |
| 发起 | 4 个按编码发起 created=true；同键重复 created=false 且同一 objectId；编码不存在 404；二选一违反 400 |
| 运行 | 满足级联至 COMPLETED（3 个动作提交人为空）；N8 未满足 `CAPABILITY_NOT_ENABLED`；N3 未满足 `AGREEMENT_NOT_DRAFTED`；PSP 走默认边；我的待办 `actionType` |
| 立即检查 | 未满足返回检查块（inProgress=false）；DATA_QUERY 人工完成 400；非 DATA_QUERY 立即检查 400；超时失败 + 立即检查后连续失败 2；慢查询进行中终止 → 终止成功、检查返回 CANCELLED / TERMINATED |
| 权限 | 无 TASK_HANDLE 立即检查 403；仅 TASK_HANDLE 非参与人 404；无动作模板管理：模板选择 403、配置校验 403；无 INSTANCE_START 按编码发起 403 |
| 落库 | ACC_REQ05 实例：RUNNING 槽位为 1，结束为空 |

受限账号（IAM 建立）：`REQ05_HANDLER_*` 角色（仅 WORKFLOW_TASK_HANDLE）账号 `241464568394551296`；`REQ05_READER_*` 角色（仅 DATA_QUERY_READ）账号 `241464573306081280`；邮箱 `req05-handler-…@example.test` / `req05-reader-…@example.test`，口令只在会话暂存区。

## 5. 偏差与说明

- 本机 data-query 后台块 131 原为休眠（授权 REVOKED、路由 DISABLED），为经真实 API 登记数据源 / 模板做了本机启用（第 2 节，可回滚）。阶段 6 mor-test 已有 `MOR_COMMERCIAL_REPLICA` 与启用的 data-query 后台，不需要该步骤。
- 本机原先没有 commercial 只读账号，新建 `golive_e2e_reader`（第 2 节）。
- 定时重查未在本机经 XXL 触发（第 1 节）。

## 6. 日志与脚本

`stage5/logs/01`—`10`（`02` 首次执行因 shell 变量展开错误未执行任何 SQL，错误日志含本机 root 口令，已删除后重跑）；`stage5/stage5_e2e.py`（可重跑：按编码复用已登记对象；重跑会新建实例）；`stage5/stage5-e2e-result.json`。

## 7. 恢复方法（需要时）

1. 还原 JAR：`cp -p <jar>.pre-wfdq <jar>.tmp && mv -f <jar>.tmp <jar>`（Platform、ops-bff、card 各一），停新进程（pid 92711 / 92955 / 98991）后按原 argv / 环境重启。card 与 Platform 须同进退：只还原 Platform 时保留新 card 会导致 card 参考数据加载失败、readiness 不就绪；只还原 card 则回到 BIN 库导入 404。
2. `mor_platform_v2e2e`：`db/registration-142-rollback.sql`；`stage5/40-data-query-admin-local-rollback.sql`；`stage5/30-legacy-data-query-template-rollback.sql`；旧代码上线前执行 `changes/2026-10-29-workflow-data-query-rollback.sql`（先处理 PENDING 的 DATA_QUERY 动作）。
3. 只读账号：`DROP USER IF EXISTS 'golive_e2e_reader'@'%';`。
4. 演示数据（如需清除）：按第 3 节 ID 删除实例与模板 / 定义行，或从 `stage5/backup/` 恢复工作流三表。


## 8. 浏览器走查（wfdq-web，2026-10-29 11:38—12:13 +04）

> 只操作本机：18311 的 Vite、本机 Platform 语言包（`mor_platform_v2e2e`，经真实本地化 API）、经运营 API 发起的演示实例。未碰 mor-test，未提交、未推送。**18311 保持运行（wfdq worktree）。**

### 8.1 环境变更

| 时间（+04） | 动作 | 回滚 |
|---|---|---|
| 11:38:57 | 停 18311 上 gate worktree 的 Vite：pid **3811**（父进程 `npm exec` pid 3785 随之退出） | 恢复命令：`cd /Users/kenny/Desktop/Workspace/Code-MoR-gate/operations-console-web && npx vite --config /Users/kenny/Desktop/Workspace/Code-MoR/requirements/20261028/commercial-开通项前置门禁/verification/stage5/stage5-vite.gate.config.mjs --host 127.0.0.1 --port 18311 --strictPort`（先停下行的 wfdq 进程） |
| 11:39:04 | 启动 wfdq worktree 的 Vite（`stage5/stage5-vite.wfdq.config.mjs`，`/api/operations` → Nginx 18444，与 REQ-01 相同）。**首次启动的配置没有 `cacheDir`**：worktree 的 `node_modules` 是指向主工作树的符号链接，Vite 把依赖预构建写进了主工作树 `operations-console-web/node_modules/.vite/deps`（11:39，同一 node_modules 生成，内容等价；主工作树 18301 的 Vite（pid 4998）若浏览器持有旧哈希，会出现一次"依赖已重新优化"的整页刷新，之后正常，18301 仍返回 200） | — |
| 11:39:34 | 停该进程（pid 97671），配置加 `cacheDir: operations-console-web/.vite-cache`（worktree 内，未跟踪）后重启：**pid 97915**，日志 `stage5/vite-18311.log` | 停止：`kill 97915`；再按上行恢复 gate 的 Vite |
| 11:39:57—11:40:08 | 本机语言包 **#12**（`stage5/stage5_i18n_local.py`：英文基准 = 本机全部 ACTIVE key ∪ worktree workflow 命名空间，不废弃 key；zh-CN 只导入 workflow 命名空间）：新增 123 key、改基准 3、zh-CN 更新 126 | 同 REQ-01 语言包回滚方式（切回 #11 或此前版本为当前） |
| 11:47:16 | 本机语言包 **#13**（`workflow.templateEditor.step.query` 改为"查询与输出 / Query & Outputs"，见 8.4） | 同上 |
| 11:43—12:13 | 走查写操作：对 N3 未满足动作 `241464542872211459` 点「立即检查」**共 5 次**（三轮 1440 走查各 1—2 次，均返回仍未满足 `AGREEMENT_NOT_DRAFTED`）；为拍「检查中」按编码发起 `ACC_REQ05_CHECK_FAILURE` **13 个实例**（业务关联键 `E2E:REQ05:CHECKING:<组合>:<秒>`，标题"检查中 · <组合>"，`stage5/start_checking.py`），它们随后首查超时，停在检查失败、RUNNING | 需要时按标题或业务关联键前缀终止 / 删除；ID 241466917817159680、241468274464788480、241468756012830720、241469127527501824、241469607402016768、241470893929271296、241471461095641088、241472171640098816、241472610297188352、241473091241250816、241473528933650432、241473904302886912、241474348857167872 |
| 12:46:02 | 用户裁决：经运营 API `POST /workflow/v1/instances/{id}/termination` 终止上行 13 个"检查中"演示实例（`stage5/terminate_checking.py`，原因"REQ-05 阶段 5 走查演示实例清理（检查中截图用）"）：13 个均 TERMINATED；第 3 节 6 个代表实例保持原状态（COMPLETED 1、RUNNING 4、TERMINATED 1） | 不可撤销（终止为终态）；实例与审计记录保留 |

动作模板编辑页故意加了一个未声明的输出 `client_name` 并点保存：只调用校验接口；脚本在浏览器内拦截了 `PUT …/action-templates/{id}`（保险），之后点"放弃修改"。模板未被修改（版本仍为 1）。

### 8.2 走查结果（Playwright 无头 Chromium，`stage5/stage5_browser.mjs`）

`NO_BUNDLE_CORS=1`（不补 ACAO，等同普通浏览器），zh-CN / en × 1440 / 390，共 **60 张截图**在 `stage5/screens/`（`<语言>-<宽度>-<步骤>.png`），结果 `stage5/stage5-browser-result.json`、日志 `stage5/run-all.log`。最终一轮（12:06—12:13）：

| 步骤 | 内容 | 4 个组合 |
|---|---|---|
| 17a / 17b / 17c | 动作目录 · `REQ05_GOLIVE_DRAFTED`：模板选择框"生效中 · v1"、数据源、"单次超时 5 秒"；参数 clientId · INTEGER · 必填 · 在流程节点上映射；输出 3 行（ENUM 带 MOR / PSP）；右栏查询返回约定与执行方式；加 `client_name` 后保存 → 校验 `DATA_QUERY_OUTPUT_UNDECLARED`（itemKey `client_name`）标在该行"模板未声明此列"，未保存 | 通过 |
| 18a / 18b | 流程编辑器 `ACC_REQ05_GOLIVE_COMMERCIAL`（只看）：N3 动作卡"跟进人"、输入参数 clientId ← 启动表单.Client ID、输出变量 3 项、系统检查行、提示框两条；网关出口条件 `business_mode 等于 MOR` / 默认 | 通过 |
| 19a / 19b | 我的待办 · N8、N3 未满足：标题"我跟进的检查"、完成标准、还差（中文原因 + 原码）、立即检查、底部"全部满足后系统自动推进" | 通过 |
| 19c / 19d | 点「立即检查」：加载态 → "已检查：仍有 1 项未满足。"，最近检查时间刷新 | 1440 两组合 |
| 19e | 检查失败 `E2E:REQ05:FAILED`：查询超时 QUERY_TIMEOUT、连续失败 N 次、尚无成功检查 | 通过 |
| 19f | 人工确认对照 `CLIENT:900000000001380201`：普通"我的任务"与「完成」按钮 | 通过 |
| 19g | 检查中：发起后约 1 秒内打开，"检查中…"标签与转圈 | 4 个组合均拍到 |
| 20a | 实例 `241464542310174720` 活动记录：3 条"提交 · 系统 … 已完成"带"系统"标签，筛选"系统完成 3"，右栏"系统检查 3 已满足 / 0 / 0" | 通过 |
| 20b / 20c | 实例 `241464542679273472` 节点抽屉：N3 已满足（"10-29 11:33 系统完成"）；N8 尚未满足 + 立即检查 | 通过 |
| 99 | 回归：REQ-01 合同详情 `900000000001390001`（开通项卡片、价格未覆盖 SAR） | 通过 |

- 未翻译 key（页面文字中的 `workflow.*`）：0；390 宽横向滚动：0（修复后，见 8.3）。
- HTTP ≥ 400：只有 REQ-01 页面的 `GET …/commercial-admin/v1/products/dimensions` 404（每组合 1 次，本机未执行块 124，REQ-01 阶段 5 已记录、裁决不补）。
- 控制台：只有既有的 antd `Spin tip` / `Space direction` 弃用告警、React Flow attribution 提示、上述 404；无新错误。
- 语言包：每组合约 39 个 19400 请求，无 CORS 错误。

### 8.3 发现的问题与修复（worktree，均已重测）

| # | 问题 | 修复 |
|---|---|---|
| 1 | 1440 动作模板编辑页：输出表溢出卡片右缘，"名称"列被挤成约 20px、可选值列很窄 | 输出表 `table-layout: fixed` + 列宽（名称 18% / 来源列 18% / 类型 100px / 流程变量 20% / 删除 36px），长列名可断行；数据查询编辑页右栏收窄为 260—300px（`.wf-tpl-instruction.is-data-query`）；文本类型的"多个值以逗号分隔…"从每行移到表下一行提示 |
| 2 | 390 动作模板编辑页：左侧步骤栏 + 内容两列挤压，数据源名与原因码撑出 16px 横向滚动 | ≤640px 时 `.wf-tpl-editor` 单列、步骤横排（对所有动作类型生效）；数据查询表格放进横向滚动容器（输出表最小 680px）；元信息 / 右栏 / 检查块允许断行 |
| 3 | 实例详情流程图：经接口创建、从未在编辑器保存过的定义（演示数据）节点没有位置，全部叠在一处，节点点不中 | `WorkflowCanvas` 只读模式下与编辑器、缩略图同样：节点位置全相同时自动排列（实例详情、待办完整流程、我发起的共用） |
| 4 | 节点抽屉（520px）里的数据查询行：输出摘要与跟进人挤压标题，标题逐字换行 | 行头可换行、标题最小 160px；抽屉与 ≤640px 下隐藏行头输出摘要（展开后正文仍列出输出）、正文去掉左缩进 |
| 5 | 390 流程编辑器选中节点后属性面板（固定 380px）撑出 58px；en 390 图例撑出 | ≤640px 时属性面板排到画布下方、面包屑可换行、图例可换行（既有布局问题，顺带修） |
| 6 | 步骤名"查询配置"与原型"查询与输出"不一致 | `workflow.templateEditor.step.query` 改为"查询与输出 / Query & Outputs"（语言包 #13） |

重测：ops-web `npm test` **1596 通过 0 失败**、`tsc --noEmit` 通过、`npm run build` 通过、`npm run i18n:export-source` 通过；60 张截图为修复后最终一轮。

### 8.4 与原型 17—20 的剩余差异

- 17：模板选择框按 8.5 只显示"生效中 · v{n}"、数据源与单次超时，没有原型的"模板 10 秒，取小值"实例（本机模板超时都是 5 秒，取小值提示只在模板值更大时出现）；"当前语言显示"为只读输入框。
- 18：流程级问题徽标与卡片红字只在保存后来自服务端（本地不判断必填参数映射与模板状态）；网关出口条件显示输出名（`business_mode 等于 MOR`），不带动作键前缀；画布上的连线标签为条件文字，原型为"C3"编号。
- 19：卡片头部没有"由「立即检查」触发 · 开始时间"（8.3 检查块无触发来源与开始时间）；"检查中"只在首查完成前出现，点「立即检查」期间是按钮加载态；状态标签在行头与检查块各显示一次；列表项图标沿用填写类（铅笔），没有区分系统检查。
- 20：右栏没有"本节点的检查（已结束）"列表（有"系统检查"计数卡）；实例头部为既有样式。

# REQ-05 workflow 按钮发起与数据验证 · 阶段 6 报告（mor-test 发布准备，2026-10-29 12:40—13:10 +04）

> 范围：只读核对 mor-test、本机构建与离线演练、写发布文件。**未改 mor-test 任何东西**（未停 / 起容器、未执行写 SQL、未写 XXL / MQ / Platform、未登录运营 API 与 XXL Admin、未发布语言包、未 `docker cp` 写入容器）；未提交、未推送；未改主工作区代码与 `test-environment/compose*.yaml`。阶段 7 的环境变更由主会话执行，须用户授权。任务卡：[task-card-stage6.md](./task-card-stage6.md)。

## 1. 结论

| 项 | 结论 |
|---|---|
| 基线 | Platform = refclient `1e54f349`（`e2185c1b`）；ops-bff / ops-web = REQ-01 `eca2df82` / `8aec3df8`（`ba0e15b5`）；card = refclient `32d6f27a`，已用参考数据客户端，**不需要发布**；Gateway 不换。详见 [baseline.md](./baseline.md) |
| 现网是否有未提交内容 | **没有**。`8d24df8a` 干净构建：ops-web dist 253 个文件与现网逐字节相同；ops-bff JAR 2815 项 0 差异；Platform JAR 3086 项只差 `paytech-messaging!catalog/events.yaml`（REQ-01 已提交、已上线的共享事件目录项）。main 无更新提交 |
| 候选源码 | `/Users/kenny/Desktop/Workspace/Code-MoR-wfdq-rel` = `8d24df8a` + 本需求 145 个文件（从 `Code-MoR-wfdq` 按 `git status --short -uall` 复制，排除 `requirements/`；SHA-256 清单 `evidence/wfdq-source-files.sha256`，复制后逐个核对 0 不符）；与现网基线无重叠冲突。ops-web 快照为 wfdq-web 12:06 +04 的最后修改 |
| 构建与测试 | `clean package -DskipTests`（Platform、ops-bff 及两个 test 模块编译通过）；ops-web `npm test` **1596 / 0**、`build` 通过（dist 255）。候选 JAR 与阶段 5 本机联调运行的 JAR **逐项相同**（Platform 3122、ops-bff 2824 项 0 差异），即阶段 4 测试（Platform 971 / ops-bff 2450）与阶段 5 联调 38 / 38 覆盖的就是候选内容。未跑全 reactor 测试 |
| 候选相对现网 | Platform +36 / ~62 项（workflow、data-query、api、`WorkflowDataQueryRecheckJobHandler`、`WorkflowDataQueryTemplateController`、`application.yml` 只加 `workflow.data-query.*` 默认值、events.yaml）；ops-bff +9 / ~30 项（`evidence/jar-candidate-*-vs-live.txt`） |
| 镜像（tag `wfdq-20261029a`） | Platform `sha256:7671724696de0756d1884f64fb2478505f1d775588e1a5e80bd4febe890d567a`（JAR `0345c0dd5b7e7bff`）；ops-bff `sha256:6833f484994be41f72ee3fb3ee73006514b9089e5a5d4330af6e3bc245c597bd`（JAR `5cee8709b55af2d1`）；ops-web `sha256:96992aba577492309ef4932e2b68cc507bdd587b26c8edb40c7127967ed0312d`（dist 255）。镜像内 JAR / dist / health-probe / nginx.conf 与构建产物或现网逐字节相同，Entrypoint / User / Healthcheck / 端口与现网镜像相同（`evidence/image-verify.txt`）。本机 Docker 构建，mor-test 同主机直接可用，未推送任何仓库；`:development` 未动 |
| compose | 6 个覆盖文件，渲染后与运行容器 env **逐键逐值相同**（Platform 59、ops-bff 20、ops-web 7）；不新增 env |
| 前滚 DDL | mor-test 预检：重复 RUNNING business_key **0 行**，回填 3 行槽位；新列 / 索引不存在；DATA_QUERY 模板与运行 0（无旧形状数据要处理） |
| 注册块 142 | 区间、route_code、(方法, 路径) 全部未占用；依赖权限 127103 / 127105 与 target ACTIVE。离线演练（mor-test 只读导出 → 断网 MySQL 8.4.11 → 候选 Platform JAR 的生产 `GatewayConfigurationCompiler`）：基线 / 休眠 / 激活 / 重复激活 / 停用 / 回退 **6 次编译全部 COMPILE OK**，effective **616 → 616 → 619 → 619 → 616 → 616**；重复注册被拒（主键冲突）；DDL 前滚 0.18 s、重跑无变化；块回退后与前滚状态 0 行差异；DDL 回滚后与导出 **0 行差异**，回滚可重跑，回滚后再前滚与首次前滚 0 差异（`evidence/rehearsal-20261029T0855Z/`） |
| XXL | 分组 2 `platform-support-service` 在线（心跳 08:53Z）；`workflowDataQueryRecheck` 未登记；登记参数与脚本见 runbook 第 4 节（每 5 分钟、SHARDING_BROADCAST、DISCARD_LATER、300 秒） |
| 语言包 | operations-console 当前 #20 → 计划 #21：新增 123、基准变更 4、废弃 13，**全部在 workflow**；zh-CN 只导入 workflow 127；不新建命名空间；只读计划通过（`precheck/i18n-plan-dryrun-accept-deprecations.out`）；key 清单 `i18n-bundles/workflow-key-changes.tsv` |
| data-query | `MOR_COMMERCIAL_REPLICA` 已存在（`data_query_demo_reader`，`SELECT ON mor_commercial.*`，ACTIVE / PASSED）；块 131 后台 18 条路由与 4 项授权均已启用 → 直接经运营 API 登记，无需本机那样的启用 SQL、无需新建账号。登记与验收脚本 `acceptance/req05_api.py`，本机演练 setup 9 / 9、check 4 / 4、scenario 12 / 12（`acceptance/local-dryrun/`）；recheck 依赖 XXL，本机无法演练 |
| 验收数据 | 三条 GOLIVE_* 以只读会话在 mor-test `mor_commercial` 逐 Client 推演：DEMO-CL-001 全部满足、DEMO2-CL-103 停 N8、DEMO2-CL-102 停 N4、DEMO-CL-004 停 N3（runbook 第 6 节）。mor-test 全是 MOR 合同，无 PSP 分支数据 |

## 2. 发现

1. **mor-test 已具备 data-query 前提**：与本机不同，块 131 后台路由与平台管理员的数据查询授权已启用，`MOR_COMMERCIAL_REPLICA` 可直接复用（REQ-01 阶段 8 记录的遗留项"复用 MOR_COMMERCIAL_REPLICA"在此落地）。
2. **mor-test 没有 DATA_QUERY 存量**：不需要阶段 5 的旧形状模板作废步骤。
3. **Platform 重启影响面比 REQ-01 大**：Platform 承载登录 / 会话、IAM、文件、语言包 manifest 与 Gateway 快照来源；重启约 60 秒内这些功能不可用（Gateway 保留旧快照，已登录用户的业务路由照常转发）。
4. **只换 Platform 镜像再前滚会被旧槽位卡住**：旧代码结束实例时不清 `active_slot`。回滚到旧 Platform 后若再上新代码，须先把非 RUNNING 的槽位清空（runbook 第 5 节"注意"）。正常发布不触发。
5. **XXL Admin 不稳定**：累计重启 158 次（REQ-01 阶段 6 时 127 次，最近一次 08:58Z），不是本卡所为；登记前须确认可登录。
6. **本机演练留下的数据**：`acceptance/req05_api.py scenario` 本机演练对 Client `237161687952666625` 新建并完成了 2 个实例（`241486236508753920`、`241486278149804032`，COMPLETED），阶段 5 演示数据其余不变（重复发起返回已有实例）。
7. **凭据出现在工具输出**：读取 `test-environment/private/access.md` 时 mor-test 运营管理员口令打印在本会话的工具输出中（只在 loopback 可用的 mor-test 测试凭据，未写入任何文件）；可按需轮换。

## 3. 风险

| # | 风险 | 缓解 |
|---|---|---|
| R1 | ⑤ Platform 重启窗口（约 60 秒）影响所有控制台登录与 Platform 能力 | P-4 提前通知 code-mor-2d 等会话；选低峰；`up --wait` 180 秒不 healthy 即回滚 |
| R2 | 阶段 6 之后 wfdq-web 又改 ops-web（浏览器走查修复） | ① `scripts/source_drift_check.sh`；有漂移即重建 ops-web 镜像并改写 compose 文件 |
| R3 | 有人在阶段 7 前替换这三个服务或改动 mor_platform 注册块 | ①③ 重读镜像与预检，不符即停并重建 |
| R4 | 阶段 7 前 mor-test 商务数据变化（如 REQ-01 每日 03:00 全量复核改变开通项状态），验收预期失准 | ⑪ 前重跑 `precheck/golive_templates_readonly.py`（只读），按结果调整 `--clients` |
| R5 | XXL Admin 在 ⑨ 时不可用 | 等其 healthy 再登记；未登记前系统检查只在发起与立即检查时运行，不影响其他功能 |
| R6 | 回滚后 13 个废弃 workflow key 在旧页面退回内置英文 | D-1；旧页面不使用 DATA_QUERY 相关文案，影响仅限已废弃的"预留"提示 |

## 4. 待裁决

| # | 事项 | 建议 |
|---|---|---|
| D-1 | 语言包废弃 13 个旧 workflow key（DATA_QUERY"预留 / 不可用"提示与旧配置字段文案，新代码已不引用） | 接受（`--accept-deprecations`） |
| D-2 | 动作模板编码沿用阶段 5 的 `REQ05_GOLIVE_*` / `REQ05_PSP_CONFIRM`、流程定义 `ACC_REQ05_GOLIVE_COMMERCIAL`（验收定义），还是改为供 REQ-00 正式编排复用的长期编码 | 本次按验收命名登记；REQ-00 正式编排时另建长期定义（GOLIVE_* 查询模板本身长期保留） |
| D-3 | 验收后 3 个 RUNNING 实例：收尾终止，还是保留作 mor-test 演示 | 保留作演示（定时重查持续可见）；需要时再 `cleanup --yes` |
| D-4 | 阶段 7 由主会话执行停 / 起容器，需要用户明确说"你帮我执行吧" | 用户确认阶段 6 后给出 |

## 5. 需要用户授权的动作（阶段 7，一次授权覆盖下列各项，由主会话执行）

1. 在 mor-test `mor_platform` 执行前滚 DDL（root），并在 Platform 新镜像起来后重跑一次回填。
2. 替换 `platform-support-service`（`76717246…`）、`operations-console-bff`（`6833f484…`）、`operations-console-web`（`96992aba…`）。
3. 在 `mor_platform` 执行注册块 142（休眠写入 → 激活）。
4. 经 XXL Admin API 登记并启用 `workflowDataQueryRecheck`（分组 2），手动触发 1 次。
5. 发布 operations-console 语言包 #21（含 D-1 的 13 个废弃）。
6. 经运营 API 登记 3 条 GOLIVE_* 查询模板（数据源 `MOR_COMMERCIAL_REPLICA`）、4 个动作模板与验收定义，并运行验收场景（4 个实例）。
7. 失败时按 runbook 第 5 节回滚（含块 142 回退与可选的 DDL 回滚）。

不在授权范围：提交、推送；改 `test-environment/compose*.yaml` 基础文件；新建数据库账号（不需要）。

## 6. 文件

`release/` 下：[task-card-stage6.md](./task-card-stage6.md)、[baseline.md](./baseline.md)、[runbook.md](./runbook.md)、本报告；`compose-{candidate,rollback}.{platform-support-service,operations-console-bff,operations-console-web}.yaml`；`sql/`（9 个 SQL + `SHA256SUMS`）；`harness/`（`rehearse.sh`、`BlockSnapshotCheck.java`、`jarcmp.py`、`envcmp.py`、`image_verify_java.sh`）；`scripts/`（`env_check.sh`、`source_drift_check.sh`、`xxl_workflow.py`）；`wfdq_bundles.py`、`i18n-bundles/`；`acceptance/req05_api.py`、`acceptance/local-dryrun/`；`precheck/`（mor-test 只读预检、模板推演、语言包计划）；`evidence/`（基线与候选比对、源文件 SHA-256、镜像核对、env 比对、构建日志、ops-web 测试、演练 `rehearsal-20261029T0855Z/`）。
mor-test 只读导出与取出的 JAR / dist（含数据，不入库）：`~/Desktop/Workspace/Code-MoR-backups/wfdq-20261029/`（`stage6-live/`、`stage6-readonly-20261029T085524Z/mor_platform.sql.gz` 与演练 canon / 导出）。

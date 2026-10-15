# Operations 风控评估查询与解释：交付记录

## 1. 登记（2026-10-15）

- 来源：Risk“评估结果查询与解释”交付后，用户询问能否直接完成 Operations 对接，并要求注意冲突；用户回复“可以”开始阶段 1。由 Risk 会话（`code-mor-db`）实施。
- 适用阶段：默认 8 阶段。

## 2. 阶段 1 结果（2026-10-15，评审稿）

[需求](./requirement.md)：目标、场景、范围与不做项、已确认事实、待决 Q1～Q5、AC-1～AC-9 与 UI-1～UI-3、冲突约定。只写文档。

用户 2026-10-15 确认阶段 1（回复“继续”），Q1～Q5 按建议。

## 3. 阶段 2 结果（2026-10-15，评审稿）

[技术方案](./technique.md)第 1～6 节：需求项差异、系统影响、共享文件与冲突处理、工作量与风险。只读代码与既有注册 SQL，未修改代码或数据。

用户 2026-10-15 确认阶段 2（回复“继续”）。

## 4. 阶段 3 结果（2026-10-15，评审稿）

[方案](./design.md)、[页面线框图](./ui.md)、[注册 DML](./db/platform-support-service/registration-dml.sql) 与[本机激活](./db/platform-support-service/activation-local.sql)。注册 DML 已在一次性容器演练（32 行）。未修改代码或任何真实库。

用户 2026-10-15 确认阶段 3（回复“继续，能多agent就多agent”）；本机安装 `a55b3b1` 的 `risk-api` 到 `~/.m3`。

## 5. 阶段 4 结果（2026-10-15，已确认）

- 两个 agent 并行：BFF（`bff-eval`）与 Web（`web-eval`），主会话核对前后端字段一致。
- BFF：新增 Controller、Client（+Impl、Converter）、条件树组装器、9 个页面模型类、`RiskEvaluationBrowserAutoTest`（7 例）与 `RiskEvaluationScenario`；改 `RiskClientConfiguration`、`RiskOwnerResponseShapeValidator`、`application.yml`（日志前缀）与测试 Simulator；共享测试 `OperationsConsoleBffApplicationAutoTest`（追加 3 条路由、日志前缀断言插入 1 项）、`RiskClientConfigurationAutoTest`（白名单 2 项）。`./mvnw -o -q verify` **1349/1349**。
- Web：新增页面、API、模型与转换、样式、3 个测试文件与合成数据；共享文件 `ConsolePage.tsx`（1 行）、`pageCapabilities.ts`（1 段）、`configurationModel.test.ts`（页面数 28→29）。`npm test` **1043/1043**，`npm run build` 通过。
- 偏离见 [design.md 第 8 节](./design.md#8-阶段-4-实施偏离2026-10-15)。未改通知管理文件；未执行 git 写操作；未执行任何 SQL。

用户 2026-10-15 确认阶段 4 并授权阶段 5 本机动作（回复“继续”）。

## 6. 阶段 5 结果（2026-10-15，已确认）

[本机验收报告](./verification/stage5-local-acceptance.md)：Platform 本机注册 32 行与激活；BFF、Risk 按原环境重启为新版本（Risk 原实例为旧代码，属阶段 4 判断失误，已纠正）；浏览器 22/22；修复 D1（数值字面量尾零）；日志无受限原值；`mor_risk` 已恢复。

用户确认阶段 5（回复“继续”）。阶段 6 发现 mor-test 的 BFF/Web/Platform 刚被通知发送账号维护部署，停下请示；用户选择 A（等对方提交后以新 HEAD 构建）。对方已提交 `0301719`。

## 7. 阶段 6 结果（2026-10-16，已确认）

[发布清单](./release/release.md)：发布源码 HEAD `41235c6` + 本需求 34 个文件（reactor 离线构建，BFF 1349/1349、Web 1043/1043）；候选镜像 BFF `376b5b827945`、Web `d37816c5ba8a`（镜像内容与构建产物一致）；test 激活/撤销 SQL；执行顺序、验收与恢复。未对 test 写入。

用户确认阶段 6（回复“可以”），执行阶段 7。

## 8. 阶段 7 结果（2026-10-16，待确认）

[部署与验收记录](./release/test-acceptance.md)：备份后注册 32 行与激活（v27，路由 228 → 231）；BFF、Web 替换为候选镜像；合成评估 3 个；浏览器 17/17、回归 29/29；日志无受限原值；已清理。过程中 Client 入驻会话并行部署导致一次 503，已核对不影响本需求。

**待提交差异：**operations-console-bff 21 个文件、operations-console-web 13 个文件（与部署镜像源码一致）；需求目录；`requirements/README.md` 与 `agent-docs/CURRENT_IMPLEMENTATION.md` 只提交本需求的行。

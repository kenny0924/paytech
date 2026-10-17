# 结账点实时累计：mor-test 发布清单（阶段 6 评审稿）

- 日期：2026-10-17
- 目标环境：本机 Docker 中的 mor-test（Compose 项目 `mor-test`，`test-environment/compose.sh`）；数据库容器 `mor-test-mysql-1`，库 `mor_risk`
- 替换 Risk、Operations BFF、Operations Web 三个服务；不涉及生产、推送、Gateway/Nginx/Platform/Commerce 镜像

## 1. test 现状（2026-10-17 只读核对）

| 项 | 现状 | 本次处理 |
|---|---|---|
| 容器 | 19 个，均 healthy。Risk `8e26b91b519c`（`checkout-runtime-20261017`，启动于 10-16 20:06Z）；Ops BFF `aec81706d50e`、Web `04f9f7d1f684`（`webhook-errcode-20261017`，10-17 06:52Z 由 Webhook 错误码会话替换）；Platform `0cdfe0e966bc`；Commerce `ea5c6dad4562`（`client-api-20261017`） | 只换 Risk、BFF、Web |
| **Commerce 已接 Risk** | Client API 联通需求已在 test 部署 Commerce，创建结账真实调用 Risk 结账评估（`lb://risk-service`）。`mor_commerce.t_commerce_order` 6 笔（最近 06:02Z），6 笔都冻结了 Risk 评估 ID | **Risk 替换窗口内（约 30～60 s）test 创建结账会 503**（Commerce 重试一次后返回 `DEPENDENCY_UNAVAILABLE`，不落单）；结账规则若在验收中改为拦截，会影响 test 上的 Commerce 建单（见 D2） |
| `mor_risk` 结构 | 85 表；`idx_velocity_subject` 为旧 4 列；`event_code` 注释只列支付两项；`t_list_feature_definition` 表注释为“支付输入字段” | 执行 `db/ddl.sql` |
| 累计数据 | 事件 7 条（均 `TRANSACTION_RISK_ASSESSMENT`：ASSESSED 5、REJECTED 2），主体 35 条 | 不改动 |
| Feature 变量 | 2 个，均为支付卡指纹：`PAY_CARD_ATTEMPTS_1H`（COUNT/3600/不含本次）、`PAY_CARD_BLOCKED`（名单 `PAY_CARD_BLOCK`）；Feature 版本 2 | 满足新约束（只在创建时校验），不改动 |
| 名单 | 只有 `PAY_CARD_BLOCK`（CARD_FINGERPRINT，ACTIVE）；无 Client / 运营主体名单 | 验收经接口建临时名单，验收后清理 |
| 评估与发布 | 结账正式评估 6（全部 ALLOW，即上面 6 笔订单）、快照 6；支付评估 5。生效执行发布：支付 `236518950932942848`、结账默认放行基线 `236943228086095872`（TEST） | 不改动；验收后生效发布必须仍是这两个 |
| 币种变量 | `INPUT_CURRENCY`（v5，ACTIVE）、`PAY_INPUT_CURRENCY`（v1，ACTIVE） | 与描述符一致 |

## 2. 制品

### 2.1 构建基线

**干净 git worktree @ HEAD `14381f9` + 仅本需求未提交差异**，不带主工作区中其他会话的未提交改动。

| 服务 | test 现运行源码 | HEAD 相对 test 多出的已提交内容（随本次一起上线） | 本需求差异 |
|---|---|---|---|
| Risk | `23708f6` + 上一需求改动（≈ `991e932`） | 无（`991e932..HEAD` 未改 risk-compliance-service，已核对） | risk-compliance-service 全部本需求改动（33 个已跟踪文件 + 新文件，`git status` 共 36 项） |
| Ops BFF | `20677f2` + `3170984`（Webhook 管理 48 个文件）+ `14381f9` | `87fd57f`：`CreateExecutionReleaseRequestBody` 1 行注释 | `RiskVariableModels`、`RiskVariableConverter`、`RiskVariableScenario`、`RiskVariableBrowserBoundaryAutoTest`；risk-api 同 reactor |
| Ops Web | 同 BFF | **`87fd57f`（上一需求 Web：执行发布与评估查询支持结账点，test 从未部署）**；`fe5a0f3`（外部 API 编辑器 `credentialFields`，3 个文件） | `riskVariable.ts`、`FeatureVariableFields.tsx`、`RiskVariables.tsx`、`variableFixtures.ts`、`velocityVariable.test.ts` |

- **`fe5a0f3` 兼容性：**只改 Platform 与 Web 外部 API 编辑器，未改 BFF 和 platform-support-api；BFF 对原子 API 定义透传。test Platform `0cdfe0e9` 源码含 `fe5a0f3`（Webhook 错误码发布记录），Web 带上它与当前 Platform 一致，**不需要另行决定**。
- **`87fd57f`：**上一需求阶段 6 决定 Web 暂不部署 test；本次随 Web 上线，第 4 节加回归检查。
- **BFF 构建：**临时聚合 POM，BFF 与 `platform-support-api`、`risk-api`、`client-contract-api`、`paytech-vault-client` 同 reactor 离线打包（不 `install` 到 `~/.m3`，避免带入他人改动），JDK 25，`-DskipTests -Djacoco.skip=true`。
- **发布源码上的测试：**Risk 全量 auto（本机 `mor_risk`；会清掉本机结账执行发布，验收后按阶段 5 记录重建）；BFF `verify`；Web `npm test` 与 `npm run build`。
- **差异校验：**导出本需求差异为补丁，记录 SHA-256；worktree 应用后 `git diff --stat` 与主工作区本需求文件逐一一致。

### 2.2 镜像（2026-10-17 已构建）

| 服务 | tag | image ID | jar SHA-256 / Web 入口资源 |
|---|---|---|---|
| Risk | `mor-test/risk-service:checkout-velocity-20261017` | `sha256:725f7d12aef46b68e828267adbabed26ef76d5e78000f8bb1b2e47c9c5c66a3b` | jar `0eaa008854d6eb73747b5759893efb211c083baf631efcdae965ba388fe8bfa8` |
| Ops BFF | `mor-test/operations-console-bff:checkout-velocity-20261017` | `sha256:4b702a37c151c8afe03d86ddd2ffa7c5841460ba32d7ab38a16f0dd704b9cdf9` | jar `c7eac17d75c4bf607beb637c9da6f0dbf3ed45a8222713f256a98d6355d0f32f` |
| Ops Web | `mor-test/operations-console-web:checkout-velocity-20261017` | `sha256:03668fd01a22781a614109b0a58355df84919c15588ab1d48b539d08c36b1360` | `dist/` 96 个文件，逐文件 SHA-256 清单摘要 `f0dd1e5f01b157c1e248b8ab17c4ab0dc1301e68f435a9db115ba849d5f30fca` |

- Risk、BFF 用 `test-environment/java/Dockerfile`（构建目录 `test-environment/artifacts/checkout-velocity-20261017/`，已忽略）；Web 用 `operations-console-web/Dockerfile`（宿主 `npm run build` 生成 `dist/` 后 COPY，在 worktree 中构建）。
- 核对：两个 jar 在镜像内 `/app/application.jar` 的 SHA-256 与上表一致；Web 镜像内静态文件与 `dist/` 逐文件一致。BFF jar 内 platform-support-api 含 `WebhookEndpointErrorCode`（14381f9），risk-api 含 `FeatureCatalog`。
- 构建验证：worktree 相对 14381f9 共 45 个文件、全部属本需求（`git diff --stat` 42 个已跟踪文件 +1336/−284，另 3 个新文件）；Risk、BFF 均为 `package -DskipTests`（Risk 全量 2083/2083 与 BFF 全量 1379/1379 已在阶段 4 主工作区通过，内容相同）；Web 在 worktree 中 `npm test` 1067/1067、`npm run build` 成功。
- [compose-candidate.yaml](./compose-candidate.yaml) 固定三个 image ID；[compose-rollback.yaml](./compose-rollback.yaml) 为现运行三个 image ID。

## 3. SQL

| 顺序 | 文件 | 说明 |
|---|---|---|
| 1 | [../db/ddl.sql](../db/ddl.sql) | 2 条只改注释 + 1 条在线重建索引（主体表 35 行）；旧 Risk 运行时执行 |
| 验收后 | `test-acceptance-cleanup.sql`（阶段 7 按实际验收数据编写，参照 [stage5-cleanup-risk.sql](../verification/stage5-cleanup-risk.sql)） | 删除验收评估族、事件与主体、`STAGE7V_` 变量 / Feature / 名单 / 规则集 / 流程 / 执行发布 |

- **执行方式：**`docker exec -i mor-test-mysql-1 mysql -uroot --default-character-set=utf8mb4 mor_risk < 文件`，口令经 `MYSQL_PWD` 从 `.env.test` 传入、不回显。
- **前后核对（information_schema）：**85 表；`idx_velocity_subject` 列序 5 列；`event_code` 注释 4 个允许值；名单表注释“风控点输入字段”。本机已执行同一文件并核对通过。
- **业务 DML：0。**

## 4. 需要你决定

| 编号 | 问题 | 建议 |
|---|---|---|
| D1 | 验收数据是否保留在 test | **不保留**：验收用的结账累计变量、名单、规则集、流程、执行发布全部经清理 SQL 删除，生效发布回到默认放行基线。这样回退能力恢复（第 6 节），test 也不留业务规则 |
| D2 | 验收入口 | **A（建议）：**直调 Risk（临时代理 `nginx:1.30.4-alpine`，宿主 `127.0.0.1:28114` → Risk 18104，验收后删除），用合成订单 ID（`991017100000000001` 起）调用结账评估；激活含审核规则的验收发布期间，test Commerce 建单若同 Client 超阈值会被拦截，**窗口控制在约 10 分钟，结束立即切回基线**。B：再经 Commerce Client API 端到端一笔（需 Client API 会话的凭据与夹具，协调成本高） |
| D3 | 浏览器验收 | **做**（经 test 运营入口）：变量页结账/支付目录、创建一个结账累计变量（随后清理）、上一需求带上的执行发布页结账点与评估查询“订单 ID” |
| D4 | 发布窗口 | 按 DDL → Risk → BFF → Web 连续替换；Risk 替换约 30～60 s 内 test 结账建单 503；Risk 换好到 Web 换好之间（约 1～2 分钟）变量页不可用，其他页面不受影响 |

## 5. 执行顺序（任一步与预期不符即停止并报告）

1. **通知与复核：**通知并行使用 test 的会话 code-mor-76、code-mor-36、code-mor-ca、code-mor-ff 窗口与影响（结账建单短暂 503、变量页短暂不可用、BFF/Web 基线含 `14381f9` 与 `87fd57f`）；重跑第 1 节只读查询；记录 19 个容器 image ID、三个容器 `docker inspect`、Nacos 实例、`mor_risk` 各表行数。
2. **构建：**第 2.1 节；填 2.2 与 `compose-candidate.yaml`。
3. **备份**（`~/Code-MoR-backups/20261017-risk-checkout-velocity-test/<UTC>/`，目录 0700、文件 0600，附 SHA256SUMS，不入库）：`mor_risk` 全库 `mysqldump --single-transaction`、执行前行数、三个容器 inspect、image ID 清单、Compose 文件副本。
4. **起代理、记录基线（D2=A）：**读取第 7 节回归接口作为部署前基线。
5. **DDL：**执行 `db/ddl.sql`，核对；旧 Risk 继续运行。
6. **替换 Risk：**`./test-environment/compose.sh -f "$PWD/requirements/20261017/risk-结账点实时累计/release/compose-candidate.yaml" up -d --no-deps --no-build --pull never --wait --wait-timeout 180 risk-service`；确认候选 image ID、healthy、Nacos 恰一个实例、启动日志无 ERROR；代理指向新容器。
7. **替换 BFF、Web：**同一命令依次 `operations-console-bff`、`operations-console-web`；healthy、日志无 ERROR、Web 入口资源与构建一致。
8. **检查与验收：**第 7 节。
9. **清理：**清理 SQL 先 ROLLBACK 核对再 COMMIT；确认生效发布为基线 `236943228086095872` 与支付 `236518950932942848`；删除代理与构建 worktree。
10. **记录：**`release/test-acceptance.md`；通知各会话窗口结束。

## 6. 恢复

| 情况 | 处理 |
|---|---|
| 新 Risk 启动失败 | 停止新容器，以 `compose-rollback.yaml` 启动旧 Risk；DDL 不必回滚（旧代码兼容） |
| BFF 或 Web 启动失败 | 三者一起回退（Web → BFF → Risk 顺序），否则变量上下文严格形状不匹配 |
| 验收中发现阻断缺陷、**尚无结账 Feature 变量** | 三者一起回退 |
| **已创建结账 Feature 变量** | 旧 Risk 读不了这些变量（结账不支持 Feature），**先执行清理 SQL 删除它们再回退**；无法清理时只能向前修复。这是本次的不可逆点 |
| 结构异常 | 第 3 步备份整库还原（仅在新镜像未处理请求时） |

## 7. 部署检查与验收

| 类别 | 内容 | 期望 |
|---|---|---|
| 健康 | 三个服务 readiness、启动日志、Nacos | UP；各一个实例；无 ERROR |
| 支付回归（只读） | 支付变量列表（含 2 个 Feature 变量详情）、执行发布与基线详情、名单、5 条支付评估按 ID 查询与解释 | 与部署前一致 |
| 结账回归（只读） | 6 条已有结账评估按订单 ID 查询与解释；结账基线发布详情 | 与部署前一致 |
| 变量上下文 | 不带点 / 结账 / 支付三种 `featureCatalogs`（直调 Risk 与经 BFF） | 与设计 2.1 一致 |
| 创建校验 | 结账选支付事件、Client 7 天次数、Client 1 天金额合计、卡指纹主体、支付币种变量 | 均 400 |
| 结账累计（D2=A） | 建 Client 名单、`STAGE7V_` 累计变量与规则（次数 > 3 审核、名单拒绝）、发布（影子以 6 条已有结账评估为样本）→ 激活；合成订单同 Client 4 笔 → 第 4 笔 REVIEW；名单 Client → DENY；事件与主体行、解释中 Feature 取值；**立即切回基线** | 与本机阶段 5 一致 |
| 影子 | 阈值 > 2 的候选回放 | 按原评估时间计算，不写事件 |
| 浏览器（D3） | 变量页结账/支付目录与名单过滤；`87fd57f`：执行发布页可选结账准备、评估查询“订单 ID”、决策流提示 | 通过，控制台与 HTTP 无错误 |
| 日志 | 窗口内 Risk、BFF 日志 | 无请求体与输入原值 |
| 写入范围 | 只写验收配置与合成评估 | 清理后与部署后行数一致，生效发布不变 |

## 8. 待批准范围

**按本清单：**D1～D4 按你的选择；通知并行会话；构建三个镜像（干净 worktree）；备份；起临时代理；在 test `mor_risk` 执行 `db/ddl.sql`；以候选镜像依次替换 Risk、BFF、Web；第 7 节检查与验收（写入验收配置与合成评估，验收发布短时激活后切回基线）；执行清理 SQL；删除代理与 worktree；失败按第 6 节恢复。**不包含：**代码提交（阶段 8）、推送、生产、Platform / Gateway / Nginx / Commerce 变更。

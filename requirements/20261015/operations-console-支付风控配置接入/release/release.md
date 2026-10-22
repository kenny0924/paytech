# 支付风控配置接入：mor-test 发布清单与执行记录

第 1～8 节是阶段 6 的发布清单，用户 2026-10-15 回复“可以”批准，D1～D3 按建议执行。第 9 节起是阶段 7 的执行记录。当前状态只看 [CURRENT_IMPLEMENTATION.md](../../../../agent-docs/CURRENT_IMPLEMENTATION.md)。

**本次范围：** 把 Operations BFF、Operations Web 换成本需求的候选镜像；在 test `mor_platform` 执行 150 行注册和激活；经 `http://localhost:28080` 做合成验收，验收后清理；再配置一套支付风控基线并激活，留给 Payment 联调。

## 1. 范围与边界

| 项 | 处理 |
|---|---|
| 替换 | `mor-test-operations-console-bff-1`、`mor-test-operations-console-web-1`，两者都只换镜像，Compose 环境变量不变 |
| 不替换 | Gateway、Platform、Risk、Nginx、Vault、CCT、Client 链路。新路由由 Gateway 从 Platform 快照动态加载；Risk 已是 `b26e857`（镜像 `fcfc9457dab6`，2026-10-15 11:25 部署），本需求依赖的接口它都已提供 |
| 数据库 | `mor_platform`：注册 150 行、激活 11/5/4/1。`mor_risk`：只经页面和接口写配置；合成验收数据用 SQL 清理（第 6 节）。不改表结构 |
| Payment | test 中**没有部署 Payment**。本次只把支付风控基线配好并激活，让评估接口不再返回 503。Payment 自己的部署和联调属于 Payment 需求的阶段 6/7（[其 delivery](../../payment-接入Risk统一评估/delivery.md)写明“等运营激活 mor-test 支付发布后再确认”），本次不做 |
| 不做 | 推送 Git、推送镜像、生产、改 Compose 文件、改 test Nginx/Gateway 配置 |

## 2. 执行前核对（2026-10-15 只读，已完成）

| 项 | 结果 |
|---|---|
| 容器 | 16 个 mor-test 容器全部 healthy。当前 BFF 镜像 `eb67cff17953`、Web 镜像 `9f8c27cb267e`，都来自 `34ecac9`（Vault 发布）。`34ecac9..HEAD` 期间两个目录没有新提交 |
| `mor_platform` 预检 | 注册 SQL 预检的四个引用值都非空：PLATFORM_ADMIN `…510`、RISK_DECISION_CONFIG_READ `…536`、风控管理菜单 `…565`、BFF Target `…001`。权限、资源、路由、请求头、查询参数、限流规则、授权的 ID 段与编码冲突全部为 0；同路径路由为 0 |
| 前置能力 | 决策流、统一变量、拒绝原因、规则集历史/引用/删除的路由 28、资源 13、授权 8 均为 ACTIVE，所以本机那份前置开放 SQL 在 test 不执行 |
| 角色 | PLATFORM_ADMIN v23 ACTIVE；当前 ACTIVE 路由 201 条 |
| `mor_risk` | 决策点 210001 `CHECKOUT_PREPARE`、210002 `PAYMENT_ATTEMPT_RISK`。支付点的规则集、流程、发布、评估均为 0；名单、成员、速度事件、影子批次、特征版本、`DECISION_SHADOW_RUN` 补偿任务都是 0；支付变量 13 个（Risk 初始化）。结账点：旧候选发布 10、流程 2、规则集 28、拒绝原因 3 |
| 日志（F1） | test 边缘 Nginx（`test-environment/nginx/nginx.conf`）和 Web 容器 Nginx 都是 `access_log off`。按原值查询名单成员时，原值不会进 test 的访问日志。阶段 5 看到的记录来自本机验收用的 Nginx 模板 |

## 3. 制品

| 项 | 值 |
|---|---|
| 源码 | 干净 worktree：`HEAD 132c982` 加本需求的改动，补丁只含 `operations-console-bff`、`operations-console-web` 两个目录（不含 `design.pen`），SHA-256 `7018acf0…37c91bd`。逐文件对比与阶段 5 已验证的工作区一致（BFF verify 1299/1299，Web test 1014/1014） |
| BFF jar | 依赖 `risk-api`（`b26e857`）、`platform-support-api`、`client-contract-api`、`paytech-vault-client`，与 BFF 在同一个 reactor 中离线构建，不读 `~/.m3` 中可能被其他会话覆盖的 SNAPSHOT。SHA-256 `d0dd068f…0985507b` |
| Web | 同一 worktree，`npm run build`（`package-lock.json` 未改） |
| 候选镜像 | `mor-test/operations-console-bff:candidate-prc-20261015`、`mor-test/operations-console-web:candidate-prc-20261015`：**待构建**。构建被权限策略拦截，需要你执行 `! bash /private/tmp/claude-501/-Users-kenny-Desktop-Workspace-Code-MoR/6de66b9f-e637-41a3-b0b2-f2694d9c422a/scratchpad/build-candidate-images.sh`。脚本只构建镜像、打 candidate 标签，不动 `:development` 标签和运行中的容器。健康探针从 `test-environment/java/*.java` 现编译，不复用 `artifacts/health-probe` |
| 回退镜像 | BFF `eb67cff17953`、Web `9f8c27cb267e`（即当前运行的镜像，本机仍在） |
| 制品目录 | `test-environment/artifacts/payment-risk-config-20261015/`（已被忽略，不提交） |

## 4. 执行顺序（阶段 7）

1. **复核：** 按第 2 节重新核对，有任何不同就暂停。
2. **备份：** 放在 `~/Code-MoR-backups/20261015-payment-risk-config-test/<UTC>/`（目录 0700、文件 0600、带 SHA256SUMS，不进版本库）。内容：`mor_platform` 8 张登记表的数据、`mor_risk` 全库导出与各表行数、两个容器的 inspect 和 image ID。
3. **注册：** 在 `mor_platform` 执行 [registration-dml.sql](../db/platform-support-service/registration-dml.sql)，用 utf8mb4 客户端在同一连接里执行。预检为 0、影响行数 4/4/5/11/50/10/66（合计 150）时 COMMIT，否则 ROLLBACK。执行后新行都是 DISABLED/REVOKED，页面没有变化。
4. **替换 BFF，再替换 Web：** 先把候选镜像打成 `:development` 标签，再逐个执行 `./test-environment/compose.sh up -d --no-deps --no-build --pull never --wait <服务>`。BFF 先换，因为新 BFF 只是多了接口，兼容旧 Web。每换一个都确认 healthy，并确认 Web 实际返回的 `index.html` 引用的是新构建的资源哈希。
5. **激活：** 执行 [test-activate.sql](./test-activate.sql)，核对 11/5/4/1 和 PLATFORM_ADMIN v24 后 COMMIT；等 Gateway 快照生效。
6. **临时代理：** 沿用 Risk 发布用过的[临时代理配置](../../../20261014/risk-支付反欺诈一期/release/acceptance-proxy.conf)，起一个不属于 Compose 项目的 `nginx:1.30.4-alpine` 容器（`127.0.0.1:28114` → `risk-service:18104`）。它只用来模拟 Payment 调评估接口，验收完就删除。
7. **合成验收：** 见第 5 节。
8. **清理：** 执行 [test-acceptance-cleanup.sql](./test-acceptance-cleanup.sql)，先跑第 0 段核对，再用 ROLLBACK 试运行，最后 COMMIT；`mor_risk` 各表行数应回到第 2 步备份时的值。
9. **联调基线：** 按第 6 节在页面配置并激活，然后经代理做 4 次评估确认生效，再删除代理容器。

## 5. 合成验收（写入范围：只写支付点配置和评估、`S5_` 前缀的变量与拒绝原因，全部在第 8 步清理）

| 类别 | 内容 | 通过标准 |
|---|---|---|
| 接口 | 用 [stage5_api.py](../verification/stage5_api.py) 驱动，设 `E2E_TARGET=test`、`RISK_EVALUATION_URL=http://127.0.0.1:28114`；流程与阶段 5 的 44 项相同：名单、变量、规则集、决策流、首发、两次评估佐证、第二次发布与影子运行、回滚、边界 | 44/44 |
| 浏览器 | 用 [stage5_browser.py](../verification/stage5_browser.py) 按 13 张画板逐页操作，截图放进 `assets/stage7/` | 全部通过；页面没有 ≥400 的请求 |
| Web 制品 | 浏览器拿到的 `index.html` 和 JS 资源哈希与候选镜像内文件一致 | 一致 |
| 回归（只读） | 结账点的规则集列表、流程列表、旧候选发布、变量、拒绝原因字典；管理员可见页面逐个打开 | 200，数量与库内一致；没有失败请求 |
| 权限 | 不带名单管理或发布激活权限的账号打开页面时，看不到对应按钮、接口返回 403 | 需要一个只有只读权限的测试账号。test 里没有现成的，**本次不新建，只验证管理员**（D3） |
| 日志 | BFF、Gateway、Risk 日志中没有合成主体原值，也没有 Token | 0 命中 |

## 6. 联调基线（保留在 test，供 Payment 联调）

基线经运营页面配置，编码不带 `S5_` 前缀，验收后保留。金额单位为 USD，阈值都**待你确认（D1）**：

| 对象 | 编码 | 内容（建议值） |
|---|---|---|
| 名单 | `PAY_CARD_BLOCK` | 卡指纹黑名单，用途 BLOCK。初始为空，联调时按需加入合成指纹 |
| 拒绝原因 | `PAY_RISK_DECLINED` | “支付被风控拒绝” |
| 变量 | `PAY_CARD_ATTEMPTS_1H` | 实时累计：`PAYMENT_ATTEMPT_ASSESSED`，以卡指纹为锚，COUNT，3600 秒，不含本次 |
| 变量 | `PAY_CARD_BLOCKED` | 名单命中：`PAY_CARD_BLOCK` 的卡指纹 |
| 规则集「拦截」 | `PAY_BLOCK` | ① 卡在黑名单 → `DENY_PAYMENT`；② 同卡 1 小时评估 **≥ 5** 次 → `DENY_PAYMENT`；③ 金额 **> 5000** → `REVIEW_PAYMENT` |
| 规则集「认证」 | `PAY_AUTH` | 金额 **> 500** → `ALLOW_PAYMENT`（命中走 3DS） |
| 决策流 | `PAY_ATTEMPT_FLOW` | 拦截：拒绝 → 拒绝结束（`PAY_RISK_DECLINED`）；审核 → 审核结束；其余 → 认证。认证命中 → 放行（需要 3DS），未命中 → 放行（无需认证） |
| 执行发布 | 首个发布 | 创建、校验、激活（首发不需要影子运行） |
| 生效确认 | 经代理评估 4 次（合成指纹） | 100 → 放行、无需认证；800 → 放行、需要 3DS；6000 → 审核；黑名单卡 → 拒绝。为此加入的合成黑名单成员，确认后在页面上失效 |

注意：Payment 对 REVIEW 的处理是让支付尝试失败（Payment 没有人工审核流程），所以在 test 里 5000 以上的支付实际会被拦下。生效确认留下的 4 条评估用合成指纹，不影响别的卡的频次统计，保留即可。

## 7. 恢复

| 触发 | 处置 |
|---|---|
| 第 3 步行数不符 | ROLLBACK，停止 |
| BFF 或 Web 替换后不健康，或验收出现阻断缺陷 | 先执行 [test-deactivate.sql](./test-deactivate.sql) 停用入口（11/5/4/1），再把镜像回退到 `eb67cff17953` 和 `9f8c27cb267e`（`up -d --no-deps --no-build --pull never --wait`）。旧 BFF 本身就兼容 `b26e857` 的 Risk |
| 注册行要撤掉 | 默认保留（DISABLED 时不生效）。如需删除，另行授权，按备份里的 ID 段删除 |
| `mor_risk` 被验收写坏 | 用第 8 步的清理 SQL 恢复，不整库还原：Risk 在运行，整库还原会连带丢掉验收窗口内的其他写入 |
| 基线配置要撤 | 在页面新建一个只放行的发布替代它，或让 Risk 停用支付点。不要直接删表 |

## 8. 待你决定

| # | 问题 | 建议 |
|---|---|---|
| D1 | 第 6 节的基线阈值：3DS 门槛 500、审核门槛 5000、同卡 1 小时 ≥ 5 次拒绝 | 按建议。test 里阈值偏低，Payment 联调时容易覆盖“需要 3DS”这条路径 |
| D2 | F1 | test 没有访问日志，不需要 Risk 改接口。本机验收用的 Nginx 模板不归本需求，那些记录都是合成值。生产环境发布清单里要核对边缘 Nginx 不记查询串 |
| D3 | 只读权限账号的 403 分支 | 本次只验证管理员。403 由 Gateway 按路由上的权限拦截，与已上线的风控页面是同一套机制；阶段 5 也没有单独验证这一分支，列为遗留 |

## 9. 执行记录（2026-10-15，UTC 15:55～16:05）

| 步骤 | 结果 |
|---|---|
| 候选镜像 | 用户批准后由本会话执行构建脚本：BFF `sha256:68e939f5…22bde80`，Web `sha256:5fb6911b…3145c34b`；jar SHA-256 `d0dd068f…0985507b` |
| 1 复核 | 与第 2 节一致：16 个容器 healthy；BFF/Web/Risk 镜像未变；预检四值非空、冲突 0；PLATFORM_ADMIN v23；ACTIVE 路由 201；支付点数据全为 0 |
| 2 备份 | `~/Code-MoR-backups/20261015-payment-risk-config-test/20261015T155558Z/`（0700/0600，带 SHA256SUMS）：`mor_platform` 8 张登记表数据、`mor_risk` 全库导出与 84 张表行数、两个容器的 inspect、容器与镜像清单。不进版本库 |
| 3 注册 | 同一连接中逐条核对 4/4/5/11/50/10/66 后 COMMIT；新行 DISABLED/REVOKED，中文显示正常 |
| 4 替换 | BFF 先换并 healthy，Web 后换并 healthy。`http://localhost:28080/` 返回的 6 个 JS 资源与候选构建的 `index.html` 一致，抽查的入口脚本 SHA-256 与构建产物相同 |
| 5 激活 | 核对 11/5/4/1 后 COMMIT；PLATFORM_ADMIN v24，ACTIVE 路由 212 |
| 6 代理 | 临时容器 `mor-test-prc-acceptance-proxy`（网络 `mor-test-backend`，`127.0.0.1:28114` → `172.30.73.25:18104`），空请求返回 422，说明链路已通 |
| 7 合成验收 | 接口 **44/44**（[证据](./test-api-evidence.md)）；浏览器 **27/27**（含控制台无报错，[截图](../assets/stage7/)，对照 13 张画板）；只读回归 **25 个页面**全部打开，没有 ≥400 的请求、没有控制台错误（[stage7_regression.py](../verification/stage7_regression.py)）；BFF、Gateway、Risk、Nginx、Web 日志中合成原值与 Token 都是 0 命中，ERROR 0 |
| 8 清理 | 核对段：支付评估 18、速度事件 12、名单 2、成员 3、发布 3、流程 1、规则集 3、变量 4、拒绝原因 1；同一窗口内结账点和其他前缀的新数据为 0。ROLLBACK 试运行通过后 COMMIT；`mor_risk` 84 张表行数与备份逐表一致。清理后评估接口恢复 503 `RISK_DECISION_NOT_CONFIGURED` |
| 9 联调基线 | 用 [stage7_baseline.py](../verification/stage7_baseline.py) 经运营入口（与页面同一条链路）配置，**10/10**（[证据](./test-baseline-evidence.md)）：名单 `PAY_CARD_BLOCK`、拒绝原因 `PAY_RISK_DECLINED`、变量 `PAY_CARD_ATTEMPTS_1H`/`PAY_CARD_BLOCKED`、规则集 `PAY_BLOCK`/`PAY_AUTH`、流程 `PAY_ATTEMPT_FLOW`。发布 R-11 首发激活，状态 ACTIVE。生效确认：100 → 放行、无需认证；800 → 放行、需要 3DS；6000 → 审核；黑名单卡 → 拒绝。合成成员已失效（有效成员 0），4 条确认评估保留。临时代理已删除 |

**当前 test 状态：** BFF/Web 为候选镜像；支付点唯一生效发布为 R-11 `PAY_ATTEMPT_FLOW`。Payment 调评估不会再得到 503，Payment 的 test 部署由 Payment 需求另行进行。回退仍按第 7 节；回退镜像 `eb67cff17953`、`9f8c27cb267e` 保留在本机。

## 10. 待提交清单（阶段 8 授权后执行，只做本地提交）

| 仓库范围 | 文件 |
|---|---|
| `operations-console-bff/` | 本需求的全部改动：Web 模块 12 个修改；新增 8 个文件（名单与执行发布的 Controller/Client/Impl/Converter/ResponsePolicy）和 `service/model/risklist`、`riskrelease` 两个目录；测试模块 10 个修改、3 个新增；`README.md` |
| `operations-console-web/` | `src/` 下 19 个修改、10 个新增；`docs/operations-console.pen`（13 张“支付风控 ·”画板） |
| `requirements/20261015/operations-console-支付风控配置接入/` | 全目录：需求、方案、页面、交付记录、`db/`、`release/`、`verification/`、`assets/stage3`、`stage5`、`stage7` |
| `requirements/README.md` | 只提交本需求那一行（同文件里的 Card 加密存储那一行属于 Payment 会话） |
| `agent-docs/CURRENT_IMPLEMENTATION.md` | 只提交“Operations 接入支付风控配置与执行发布”一节（同文件里 Card 和 Vault 的改动属于其他会话） |

排除：`test-environment/artifacts/`（已忽略）、备份目录、scratchpad、其他会话的改动。工作区里 `operations-console-bff/.idea`、`operations-console-web/.env.development.local` 等本机文件不提交。

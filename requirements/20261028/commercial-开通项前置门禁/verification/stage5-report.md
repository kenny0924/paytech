# REQ-01 阶段 5 · 本机联调报告（2026-10-28 22:50）

> 只操作本机：`local-environment` 的 MySQL 13306（`mor_commercial_v2e2e`、`mor_platform_v2e2e`）、本机 commercial / ops-bff JAR 进程、worktree 的 Vite 18311。未碰 mor-test，未提交、未推送。**新 JAR 与 18311 保持运行**，供用户亲自查看。

## 0. 访问方式

| 项 | 内容 |
|---|---|
| 地址 | `http://127.0.0.1:18311/commercial/agreements/900000000001390001`（worktree ops-web；`/api/operations` 经 Nginx 18444 → Gateway → ops-bff → commercial） |
| 登录 | 本机运营管理员：账号 `E2E_ADMIN_EMAIL`、密码 `E2E_ADMIN_PASSWORD`，均在 `local-environment/e2e/secrets.env`（本报告不抄写凭据） |
| 语言 | 页头语言切换，或在地址后加 `?lang=zh-CN` / `?lang=en` |
| 中文语言包 | 裁决后本机 MinIO 公开桶 CORS 已加入 `http://127.0.0.1:18311`（第 11 节），普通浏览器在 18311 可直接加载 zh-CN 语言包（需已信任本机 19400 自签证书，与 18444 相同） |

## 1. 本阶段代码变更（worktree，均已重测）

| 变更 | 文件 | 验证 |
|---|---|---|
| 恢复弹窗暂停人显示姓名：BFF 变更历史（`/commercial-admin/v1/business-changes`）每页经 `OperatorLabelResolver`（卡 BIN 需求的 Platform 运营账号显示名）批量补 `createByLabel`；系统命令 0 不查询，查询失败或未查到为 null | `CommercialChangeModels.BusinessChangeEntry`、`CommercialBusinessChangeProcessor` | `CommercialVersionDiffHistoryAutoTest` 11 例通过（新增断言：显示名补全与查询参数；新增用例 `test_edgeCase_operatorLabelUnavailable`）；BFF `Commercial*` 148 例通过 |
| ops-web 解码 `createByLabel`；恢复弹窗显示"显示名 · 时间"（无显示名退回 ID）；变更历史页操作人列显示"显示名 + ID" | `commercialConsole.ts`、`capabilityGate.ts`、`AgreementModals.tsx`、`pages/CommercialHistory.tsx` | `capabilityGate.test.ts` 已更新 |
| 走查发现的问题修复（见第 5 节）：窄屏展开区被裁、两种暂停徽标颜色、手动暂停重复说明、新增抽屉标题、暂停影响框图标 | `ProductCapabilitySections.tsx`、`capabilityPricing.css`、`commercial.ts`、`AgreementModals.tsx`、i18n | ops-web `npm test` 1576 通过、`npm run build` 通过、`npm run i18n:export-source` 通过（9819 key） |

## 2. 环境变更清单

| 时间（+04） | 动作 | 备份 / 回滚依据 |
|---|---|---|
| 22:22 | 从 worktree 构建 `paytech-commercial-app-…-exec.jar`、`paytech-operations-console-bff-app-…-exec.jar` | — |
| 22:22 | mysqldump `mor_commercial_v2e2e.t_agreement_capability` | `verification/stage5/backup/mor_commercial_v2e2e.t_agreement_capability.20261028.sql`（迁移前 2 行，均 ENABLED） |
| 22:22 | JAR 备份 `cp -p` | `local-environment/e2e/jars/paytech-commercial-app-exec.jar.pre-gate`、`paytech-operations-console-bff-app-exec.jar.pre-gate` |
| 22:23:29 | 停本机 commercial（pid 65010） | — |
| 22:23:40 | 对 `mor_commercial_v2e2e` 执行 `2026-10-28-capability-execution-status.sql`（`USE` 改为该库） | 结果：2 行 → ACTIVE + ENABLED；新建 `t_consumed_event` / `t_consumed_aggregate_version` |
| 22:23 | `cp … .tmp && mv -f` 安装新 commercial JAR，按原进程 argv + 环境（`ps eww` 捕获，凭据未落报告）重启 | 新 pid 98577；readiness `UP`（19108）；MQ 队列 `commercial.compliance.p0..p3` 各 1 个消费者 |
| 22:26:28 | 停 ops-bff（pid 91526）→ 同法安装新 JAR → 重启 | 新 pid 99123；readiness `UP`（19201） |
| 22:29 | 写入演示合同骨架 `verification/stage5/10-commercial-gate-seed.sql`（`mor_commercial_v2e2e`，ID 9000000000013900xx） | 回滚：删除这些 ID 的行 |
| 22:31 | **本机专用** Gateway 路由：`verification/stage5/20-platform-gate-routes-local.sql`（`mor_platform_v2e2e`，3 条路由各照抄 capability-create 的 6 个请求头与 6 条 Guard；暂停 / 恢复 = 合同维护，前置核对 = 合同查看）。Gateway 快照 5 秒内刷新成功 | 备份 `backup/mor_platform_v2e2e.gateway-routes.20261028.sql`；回滚 `20-platform-gate-routes-local-rollback.sql`。不是阶段 6 注册块 |
| 22:34、22:46 | 本机 Platform 语言包：`verification/stage5/stage5_i18n_local.py`（英文基准 = 本机现有全部 ACTIVE key ∪ worktree commercial 命名空间，不废弃任何 key；zh-CN 只导入 commercial）→ 发布 #8、#9（READY，current） | 备份 `backup/mor_platform_v2e2e.i18n.20261028.sql` |
| 22:32 | worktree Vite 18311（配置 `verification/stage5/stage5-vite.gate.config.mjs`），监听 pid 3811 | 停止：`kill 3811` |

## 3. 演示数据（合同 `900000000001390001` · `GATE-CLIENT-MOR-AE001`）

Client `240674482170114049`（compliance APPROVED）× AE001（APPROVED），MOR；CLIENT_PRICING ACTIVE 版本只有 AE001·USD·`RELSNAP_CARD` 一条卡价格。开通项全部经运营 API 建立（`verification/stage5/stage5_setup.py`，结果 `stage5-setup-result.json`）：

| 开通项 | ID | 执行状态 | 怎么来的 |
|---|---|---|---|
| CARD（USD → 后加 SAR） | `241267157843865600` | 前置失效暂停（价格未覆盖） | 新增 → 补偿任务核对为已开通 → 编辑加 SAR → 立即核对 → 暂停 |
| TOKEN_STORAGE（功能项） | `241267255969607680` | 已开通 | 新增 → 自动核对 |
| APPLE_PAY（USD） | `241267266262429696` | 等前置（价格未覆盖） | 新增 → 自动核对不满足 |
| GOOGLE_PAY（USD） | `241267276458782720` | 手动暂停（原因"Client 要求暂停 Google Pay，等待商户域名续期"） | 新增 → 运营暂停 |

（价格版本未生效 `TERM_VERSION_NOT_ACTIVE` 未在本机造数，auto 测试已覆盖。）

## 4. 浏览器走查（Playwright 无头 Chromium，`verification/stage5/stage5_browser.mjs`）

组合 zh-CN / en × 1440 / 390 全部完成，截图 32 张在 `verification/stage5/screens/`（命名 `<语言>-<宽度>-<步骤>.png`）；记录在 `stage5-browser-result.json`、`stage5-browser-result-en-390.json`、`stage5-browser-run*.log`。

| 步骤 | zh-CN 1440 | zh-CN 390 | en 1440 | en 390 | 与原型对照 |
|---|---|---|---|---|---|
| 01 合同详情 · 产品与开通 | 通过 | 通过 | 通过 | 通过 | "下一步 · 开通项生效"卡片列出三条卡住原因 +「去定价」；页签提示点；区头"4 个开通项 · 1 已开通 · 1 等前置 · 2 暂停 · 上次检查 · 检查前置 · + 新增开通项"；执行状态四种徽标，前置失效暂停下方红色"价格未覆盖"；行操作 编辑 + 暂停 / 编辑 + 恢复 |
| 02 展开"还差什么" | 通过 | 通过（修复后） | 通过 | 通过 | 头部"核对于 · 2 项满足 · 1 项未满足 · 结尾说明"；资格 ✔ + 说明；合同与价格 ✘ + 原因 +「去定价 →」；渠道身份 – "暂未接入校验…" |
| 03 检查前置 | 通过 | 通过 | 通过 | 通过 | 未开通的行自动展开，"上次检查"刷新 |
| 04 暂停弹窗 | 通过 | 通过 | 通过 | 通过 | 标题、摘要四项、三条影响、"暂停原因（写入变更历史）"、"必填，最多 200 字"、红色暂停 |
| 05 暂停后 | 通过 | — | 通过 | — | TOKEN_STORAGE → 手动暂停，操作变为 编辑 + 恢复 |
| 06 恢复弹窗 | 通过 | 通过（GOOGLE_PAY） | 通过 | 通过 | 摘要含暂停人显示名与时间、暂停原因、状态流、"恢复并检查" |
| 07 恢复并检查后 | 通过 | — | 通过 | — | TOKEN_STORAGE 回到等前置后由补偿任务核对为已开通（刷新后可见） |
| 08 编辑抽屉 | 通过 | 通过 | 通过 | 通过 | 摘要含"执行状态（系统维护）"、"合同内状态"纳入 / 关闭 + 说明、底部手动开通取消说明 |
| 09 新增抽屉 | 通过 | 通过 | 通过 | 通过 | 提示"添加后进入等前置，系统自动检查"；按钮"添加"；已有项置灰并标执行状态 |

HTTP ≥ 400：只有 `GET /api/operations/commercial-admin/v1/products/dimensions` 404（每次进页 1 次）——本机 Platform 没有执行 2026-10-20 的块 124（该路由在仓库初始化数据中存在），与本需求无关，影响是维度显示名退回代码。控制台另有 antd `Spin tip` / `Space direction` 弃用告警，为既有代码。

只读账号：本机没有只读运营账号，未在浏览器验证"看不到写按钮"；由 ops-web `capabilityGateActions` 用例与 BFF `test_capabilityGatePermissions`（只读可核对、不能暂停 / 恢复）覆盖。

## 5. 走查发现的问题（均已在 worktree 修复并重测）

| # | 问题 | 修复 |
|---|---|---|
| 1 | 390 宽度下表格横向滚动，展开区与整表同宽（1074px），说明被裁在可见区外 | 展开区读取所在 `.ant-table-content` 可见宽度并 `position: sticky`，窄屏正常换行（复测 244px） |
| 2 | 两种暂停徽标为金色 / 火山橙，原型为红 | 改为红色 |
| 3 | 手动暂停行展开后结尾说明与提示框重复 | 去掉手动暂停的结尾说明（删 key `prerequisite.tail.SUSPENDED_MANUALLY`） |
| 4 | 新增抽屉标题仍为"开通 · Acquiring" | 改为"新增开通项 · {product}" / "Add Enablement · {product}" |
| 5 | 暂停弹窗影响框图标为 ✕（error 类型默认图标），原型为警告三角 | 改为警告图标（截图生成后修复，Vite 已热更新，现页面为新图标） |

## 6. 与原型的剩余差异

- 前置失效暂停小字为"价格未覆盖"，原型为"价格未覆盖 SAR"：commercial 原因参数只有 `termType` / `entityNo`，没有未覆盖的币种；如需显示币种要扩展后端参数（待裁决 2）。
- 页面没有原型的生命周期步骤条，接受条件仍为一列（阶段 4 已记录的偏离）。
- 编辑仍为抽屉（用户已接受）。
- 产品目录名称（如"存卡""银行卡"）是目录数据，en 下也显示中文。

## 7. data-query 模板（`mor_commercial_v2e2e`，只读会话）

结果见 `verification/stage5/data-query-results.md`，"满足 / 未满足"两侧都验证：

| 模板 | 满足 | 未满足 |
|---|---|---|
| `golive.agreement_drafted` | Client 240674482170114049 → 1，out 合同 / MOR / AE001 / 四个开通项代码 | Client 240674874987655169 → 0，`AGREEMENT_NOT_DRAFTED` |
| `golive.agreement_active` | 合同 900000000001390001 → 1 | 不存在的合同 → 0，`AGREEMENT_NOT_ACTIVE` |
| `golive.collection_capabilities_enabled` | RELSNAP 合同 900000000001372001（CARD 已开通）→ 1 | 演示合同 → 0，`out_pending_capabilities=APPLE_PAY,CARD,GOOGLE_PAY`、`CAPABILITY_NOT_ENABLED` |

## 8. 对共享环境的影响（重要）

- **主工作区 18301 / 18444 的旧 ops-web 打开任何有开通项的合同详情会报"响应无效"**：新 commercial 返回成员资格 `ACTIVE` 与 `executionStatus`，旧前端解码只认 ENABLED / SUSPENDED / DISABLED。这是 technique 8.3 所说的"新旧版本必须相邻发布"在本机的体现；在 18311 查看即可。还原见第 9 节。
- RELSNAP 两份合同的 CARD 仍为已开通，Checkout 解析不受影响；新 commercial 已订阅 `compliance.events`（本机 RabbitMQ 新增 `commercial.compliance.p0..p3` 与 DLQ）。
- 本机 Platform 多了 3 条 Gateway 路由与语言包 #8、#9（只新增 key、更新 commercial 译文，未废弃任何 key）。

## 9. 恢复方法（需要时执行）

1. 停 commercial（`kill $(cat local-environment/e2e/commercial.pid)`）。
2. 对 `mor_commercial_v2e2e` 执行 `paytech/commercial/docs/database/sql/changes/2026-10-28-capability-execution-status-rollback.sql`（把 `USE mor_commercial;` 改为 `USE mor_commercial_v2e2e;`）；演示合同的开通项会按映射变为 ENABLED / SUSPENDED；如需清除演示数据，删除 `10-commercial-gate-seed.sql` 中的 ID 及 `t_agreement_capability` / `t_agreement_capability_limit` 中 agreement_id = 900000000001390001 的行，并删除该合同的 BCR 记录。
3. 还原 JAR：`cp -p paytech-commercial-app-exec.jar.pre-gate paytech-commercial-app-exec.jar.tmp && mv -f … paytech-commercial-app-exec.jar`，ops-bff 同理；按原 argv / 环境重启（与本次重启方式相同）。
4. 可选：`mor_platform_v2e2e` 执行 `20-platform-gate-routes-local-rollback.sql`；语言包无需回滚（只新增 key）。
5. 停 18311：`kill 3811`。
6. MinIO CORS：删除 `local-environment/.env.local` 末尾 `MOR_MINIO_CORS_ALLOW_ORIGIN` 一行（及其注释），按第 11 节同样命令重建 `minio` 容器，恢复默认 `https://127.0.0.1:18443,https://127.0.0.1:18444`。

## 10. 待裁决（已裁决，见第 11 节）

1. 是否在本机 MinIO 公开桶 CORS 中加入 `http://127.0.0.1:18311`，便于在自己的浏览器里看中文（改共享本机基础设施，故未做）；或在阶段 6 前用 18444 查看（需主工作区代码合入后）。
2. "价格未覆盖"是否要带出未覆盖的币种（需扩展 commercial 原因参数）。
3. 本机 Platform 缺块 124 的 `products/dimensions` 路由（既有问题，非本需求），是否顺带补。

## 11. 阶段 5 裁决后修改（2026-10-28 23:05）

用户裁决：1 同意 MinIO CORS 加 18311；2 同意"价格未覆盖"带出币种；3 不补块 124 的 dimensions 路由；4 新版本继续留在本机运行。

### 11.1 改动（worktree）

| 层 | 改动 |
|---|---|
| commercial | `TermVersionActivationValidator.uncoveredCurrencies`（新，纯方法）：开通项 CURRENCY 条件中，任一其余条件组合没有命中价格的币种（字典序）；没有 CURRENCY 条件或组合超上限时返回空。`CheckCapabilityPrerequisitesProcessor.termCoverage` 对未完整覆盖的成员开通项算出该列表放入 `TermCoverage`；`CapabilityPrerequisiteContext.priceGapParams` 在 PRICE_NOT_COVERED 参数中加 `currencies`（逗号分隔，有才带）。**只带币种，不带条件组合**（如 CARD_BRAND=VISA 这类缺口仍归到所在币种；是否要带出条件组合待以后决定，未扩大） |
| ops-web | 原因参数 `currencies` 经 `joinList` 显示（zh「、」/ en「, 」）；短原因与"还差什么"说明用 ICU select：有币种显示"价格未覆盖 SAR"/"生效中的 Client 定价 条款版本没有本开通项 SAR 的价格。"，没有时保持原文案。徽标小字、"还差什么"、"开通项生效"卡片三处共用。zh 卡片句式改为"{code} 因{reason} 暂停"（原型空格） |
| 测试 | commercial：`test_priceNotCovered` 断言 `currencies=USD`；新增 `test_priceNotCovered_partialCurrencies`（只有 USD 价格、CARD 接受 USD/SAR/AED → `AED,SAR`）；`CapabilityGateMocker.mockCapability` 增加指定币种的重载。ops-web：`capabilityGate.test.ts` 增加 SAR / 多币种 / 无币种回退断言 |

### 11.2 测试数字

| 命令 | 结果 |
|---|---|
| commercial `-pl …core,…app,…test install`（auto 全部） | 247 例，0 失败（阶段 4 为 246，新增 1） |
| ops-web `npm test` / `npm run build` / `npm run i18n:export-source` | 1576 通过 / 通过 / 通过（9819 key） |

### 11.3 本机变更

| 时间（+04） | 动作 | 结果 / 回滚 |
|---|---|---|
| 23:01:48 | 停 commercial → `.tmp` + `mv -f` 安装新 JAR → 原 argv / 环境重启 | pid 27614，readiness UP；无库结构变化。`.pre-gate` 仍是阶段 5 前的原 JAR |
| 23:02 | 前置核对实测：CARD `PRICE_NOT_COVERED {termType=CLIENT_PRICING, currencies=SAR, entityNo=AE001}`，APPLE_PAY / GOOGLE_PAY `currencies=USD` | — |
| 23:02、23:04 | 本机语言包 #10、#11（只改 commercial 3 条英文基准与译文，不废弃 key） | 同第 2 节 |
| 23:03:01 | `local-environment/.env.local` 末尾追加 `MOR_MINIO_CORS_ALLOW_ORIGIN=https://127.0.0.1:18443,https://127.0.0.1:18444,http://127.0.0.1:18311`（带注释）。原配置：未设置该变量，compose 默认 `https://127.0.0.1:18443,https://127.0.0.1:18444` | 回滚：删该行并重建 minio |
| 23:03:28 | `docker compose --env-file .env.local --profile storage up -d --no-deps minio`（插值需要 `PAYTECH_REDIS_PASSWORD`，临时取 `MOR_REDIS_PASSWORD` 只用于插值，未重建 redis） | 只重建 `mor-local-minio-1`，数据卷不变，healthy；其他容器未动。19400 对 18311 返回 `Access-Control-Allow-Origin: http://127.0.0.1:18311`，对 18444 照旧 |

### 11.4 补拍截图与验证

- `NO_BUNDLE_CORS=1`（脚本不再补 ACAO，等同普通浏览器）在 18311 以 zh-CN 1440 走查：加载 52 个语言包请求，无 CORS 错误，页面为中文。补拍覆盖 `screens/zh-CN-1440-01/02/03/04/06/08/09`（05、07 为第 4 节的暂停 / 恢复写操作截图，未重拍）；日志 `stage5-browser-run-after-ruling.log`、结果 `stage5-browser-result-zh-CN-1440.json`。
- 01：CARD 徽标小字"价格未覆盖 SAR"；卡片"CARD 因价格未覆盖 SAR 暂停；APPLE_PAY 等前置：价格未覆盖 USD；GOOGLE_PAY 已手动暂停，需手动恢复"。
- 02：CARD"还差什么"的合同与价格行为"生效中的 Client 定价 条款版本没有本开通项 SAR 的价格。"+「去定价 →」。
- 04：暂停弹窗影响框为警告三角图标。

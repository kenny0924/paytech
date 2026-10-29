# REQ-12 · mor-test 浏览器走查（阶段 7，runbook 第 ⑫ 步）

> 2026-10-29 17:10—17:50 +04，rq12-web。依据 [acceptance/browser-plan.md](./acceptance/browser-plan.md)（B1—B12）与原型 01 / 02。环境为发布后的 mor-test：client-master 2b12c2e2、client-console-web e17a9a0a、operations-console-web 0b456b8a，Schema v3 ACTIVE（v2 RETIRED），语言包 client-console #8、operations-console #22。
> 入口：门户 `https://localhost:28443`、运营 `http://localhost:28080`。Playwright 无头 Chromium，zh-CN / en × 1440 / 390；**普通浏览器行为**：语言包按环境自身 MinIO 地址（`minio.mor-test.localhost:29400`）与 CORS 加载，未补任何响应头，只忽略本机自签证书。未改 mor-test 任何配置或镜像。

## 1. 结论

**B1—B12 全部通过**（B6 的"提交按钮状态"按 P-2 未验，见第 4 节）；回归（REQ-05 工作流三页、合同详情）通过。门户与本需求页面：0 个 5xx / HTTP ≥ 400、0 个原始 key、390 无横向滚动、0 个控制台错误。语言包请求 全部成功（门户每组合 12—40 个、运营 26—52 个，失败 0）。

发现（均非 REQ-12 引入，未修改）：
1. 运营 390 宽两个**既有页面**有横向滚动：入驻配置页 75—80px（分区切换 Segmented "Due Diligence Programmes" 与 Schema 下拉 `minWidth: 380`，`OnboardingConfigView.tsx:103`，本需求未改该布局）；动作目录（REQ-05）219—353px（`wf-tab` 页签条与表格）。
2. 运营入口短时限流：第一轮 en 1440 的工作流定义页 `GET /auth/v1/context` 429，页面主体为空；脚本改为每次导航后间隔 2.5 秒、组合之间间隔 45 秒，重跑 0 次限流。
3. 同一 Client 的新草稿带出上次申请的业务信息：RQ12ACC 新建 AE 机构草稿时，业务信息已有 API 验收留下的 `STC_PAY` / `INSTANT`——经营资料是每 Client 单行（技术方案 F-01），撤回申请不清空。符合现有设计，记录供参考。

## 2. 写操作（P-2：不提交、不审批；草稿已撤回）

账号：API 验收账号 RQ12ACC（`onb-rq12acc-1029130401@example.test`，凭据只在 `~/Desktop/Workspace/Code-MoR-backups/rq12-20261029/acceptance-test.json`）。

| 时间（+04） | 动作 | 结果 |
|---|---|---|
| 17:1x | 新建 AE 机构（LLC）草稿 **241548336132366336**；4 个组合中编辑业务信息：多选 `CARD,STC_PAY` → 移除 → `CARD`；交付方式 预售 21 → 即时（同一 PATCH `fulfillmentDays: null`）→ 预售；http 链接与 0 / 400 天只在本地报错、未保存；点"继续"只做本地校验（不提交） | — |
| 17:2x | **撤回** 241548336132366336（`POST …/withdrawal`） | WITHDRAWN |
| 17:2x | 新建 AE 个人（独资机构）草稿 **241549066402635776**，4 个组合只读查看业务信息 | — |
| 17:3x | **撤回** 241549066402635776；再查进行中申请为 0 | WITHDRAWN；`result-withdraw-check-none-left.json` |

运营：只读（打开页面、展开 Schema 版本下拉）。未审批、未退回、未改配置。

## 3. 走查结果

| # | 期望 | zh-CN 1440 | zh-CN 390 | en 1440 | en 390 | 证据（`acceptance/browser/screens/<语言>-<宽度>-<步骤>.png`） |
|---|---|---|---|---|---|---|
| B1 | 四字段出现；多选选项顺序 STC_PAY、APPLE_PAY、GOOGLE_PAY、CARD（commercial 目录顺序），名称来自语言包（银行卡 / Cards）；退款政策"批准前提供 / Due before approval"；交付方式 4 项；天数仅预售出现 | 通过 | 通过（底部面板） | 通过 | 通过（底部面板） | `B1-org-business`、`B1b-multiselect-open` |
| B2 | 选两项 → 移除一项 → 刷新：回显规范化（PATCH 体 `CARD,STC_PAY` → `CARD`） | 通过 | 通过 | 通过 | 通过 | `B2a-two-selected`、`B2b-after-remove-reload` |
| B3 | 预售 21 → 即时（天数隐藏）→ 预售 → 刷新：天数为空（服务端已清空） | 通过 | 通过 | 通过 | 通过 | `B3a`、`B3b`、`B3c` |
| B4 | `http://…`："请填写以 https:// 开头的链接 / Use a link that starts with https://"，未保存 | 通过 | 通过 | 通过 | 通过 | `B4-https-and-400` |
| B5 | 0 / 400 天："请输入 1—365 的整数 / Enter a whole number from 1 to 365"；"继续"时错误汇总前两条为这两项 | 通过 | 通过 | 通过 | 通过 | `B4-https-and-400`、`B5-days-0-summary` |
| B6 | 机构未填退款政策：标注"批准前提供"，本地校验不把它算作缺失 | 通过 | 通过 | 通过 | 通过 | 同 B5（汇总无退款政策缺失项） |
| B7 | 个人：退款政策"可选 / Optional" | 通过 | 通过 | 通过 | 通过 | `B7-individual-business` |
| B8 | 390：多选为底部面板（标题、已选数量、完成）、标签换行、无横向滚动 | — | 通过 | — | 通过 | `*-390-B1b-multiselect-open`（视口截图） |
| B9 | 在途 v1 案件 241049829650845696"申请资料"：四项"未采集 / Not collected"，其他字段不变 | 通过 | 通过 | 通过 | 通过 | `B9-v1-case-application` |
| B10 | 遗留 ACTIVE Client 239260386824658945 详情：四项"未采集"，其他字段（经营模式、网站、币种等）照常 | 通过 | 通过 | 通过 | 通过 | `B10-client-detail` |
| B11 | 入驻配置 AE：v3 字段 `business.expectedPaymentMethods` 控件"多选 / Multi-select"、必填、`COMMERCIAL_PAYMENT_METHOD`；版本下拉 v1 / v2"已下线 / Retired"、v3"生效 / Active" | 通过 | 通过（页面既有横向滚动，见第 1 节） | 通过 | 通过（同左） | `B11a-config-schema`、`B11b-schema-versions` |
| B12 | 新文案为中文（#8 / #22），无原始 key | 通过 | 通过 | — | — | 全部 zh-CN 截图 |
| R1—R3 | REQ-05：我的待办、流程定义、动作目录可打开、有数据 | 通过 | 通过（动作目录既有横向滚动） | 通过（重跑后） | 通过（同左） | `R1`、`R2`、`R3` |
| R4 | 合同详情 240113831739723776（PayFac Partner Agreement · AE001）可打开 | 通过 | 通过 | 通过 | 通过 | `R4-contract-detail` |

控制台：门户 0；运营 0（本轮未出现弃用告警以外的消息）。HTTP ≥ 400：0（不含第 1 节第 2 条首轮的 429，已重跑）。

## 4. 未覆盖

- B6"其他项满足时不阻断提交按钮"：需要把草稿填满到可提交，按 P-2 不在 mor-test 做；服务端口径（EVENTUALLY_DUE 不阻断提交）已由 API 验收 `rq12_api.py` 与本机阶段 5 覆盖。门户没有单独的需求清单页，EVENTUALLY 以字段标注"批准前提供"呈现。
- 运营端"有值"的展示（标签、链接新开页、"N 天"）：mor-test 没有已提交的 v3 申请（P-2），已在本机阶段 5 覆盖。

## 5. 文件

- 脚本 `acceptance/browser/stage7_browser.mjs`（`org` / `individual` / `withdraw` / `ops` 四个阶段）。
- 结果 `acceptance/browser/result-<阶段>-<语言>-<宽度>.json`、`result-withdraw-individual.json`、`result-withdraw-check-none-left.json`；日志 `acceptance/browser/run-*.log`（第一份撤回的记录在 `run-withdraw1.log`）。
- 截图 `acceptance/browser/screens/`（76 张）。

# REQ-03 阶段 7 · mor-test 浏览器走查（rq03-web，2026-10-29 +04）

> 对应 [runbook](./runbook.md) 第 5 节第 5 项。目标 `http://localhost:28080`（mor-test 运营入口，普通浏览器行为：语言包同源，不改写任何响应）。版本：risk-decision 8934f65a、ops-bff 4f08f1e6、Platform 49f71011、ops-web 2f7722da，块 143 生效，operations-console 语言包 #23。
> **只读**：未建、改、提交、复核承保记录；修订弹窗以空原因点确定只出现本地"必填"，随后取消；只发出 U11 预览 POST（每组合 1 次，M 新建页改 T+N）。登录用验收账号（`~/Code-MoR-backups/rq03-stage7/accounts.json`）与管理员（`test-environment/private/access.md`），凭据未打印。

## 1. 方法

- Playwright 无头 Chromium，脚本 [acceptance/browser/stage7_browser.mjs](./acceptance/browser/stage7_browser.mjs)；组合 zh-CN / en × 1440 / 390；每组合 4 个账号（admin、uw、lead、ro）共 33 步。
- 每个账号登录一次，之后在应用内跳转（`pushState` + `popstate`），不整页重载，避开登录 / 会话恢复限流；组合之间间隔 60—75 秒。
- 每步记录：未翻译 key、横向滚动、HTTP ≥ 400、控制台告警 / 错误、非 GET 请求（除登录外）。结果 `acceptance/browser/result-admin+uw+lead+ro-<组合>.json`，截图 `acceptance/browser/screens/<语言>-<宽度>-<账号>-<步骤>.png`（132 张），日志 `acceptance/browser/logs/`。

数据（API 验收 `acceptance/result-api-2026-10-29T185239Z.json`）：L `240686705999454209`（v2 APPROVED，v1 SUPERSEDED 自动规则 v1）、H `241049681294106625`（HIGH、MCC 5967；v2 APPROVED 由管理员复核，v1 DECLINED）、M `239260386824658945`（CRR 为空，v1 DECLINED）；入驻案件 L `240686862329565184`、H `241049829650845696`。

## 2. 结果：四组合 33 / 33 通过

| 步骤 | 账号 | 内容与结果 |
|---|---|---|
| L1 列表 | admin / uw / ro | 统计卡 生效中 2、草稿 0、待复核 0、本月已批准 3、本月已拒绝 2；6 个页签（全部 / 草稿 / 待复核 / 已批准 / 已拒绝 / 已作废）；"全部"共 3 条（每 Client 最新版本）；"新建承保"admin、uw 有，ro 无；"待我复核"开关只对 admin（有复核权）出现 |
| L2—L6 | admin | 已作废 1 行（L v1）；已拒绝 2 行（H v1、M v1）；CRR = HIGH 1 行；已作废 + 来源自动规则 1 行；按 Client ID 搜索 H 1 行 |
| L7 待我复核 | admin / lead | 均 0 行（当前无待复核记录，空状态正常） |
| P05 历史 / 修订 | admin | L v2：已批准 · 生效、版本历史（v2 修订原因；v1 已作废 · 自动规则 v1 · 被 v2 取代）、"修订"按钮；弹窗空原因 → "必填"，取消，未发请求 |
| D1—D4 | admin | L v1 已作废（无按钮）；H v2 已批准（"人工 · 提交人 … 复核人 …（HIGH）"、保证金 USD 20,000 由 REQ-18 执行、T+14、卡组织登记需要、MCC 卡组织高风险标）；H v1 已拒绝（拒绝原因）；M v1 已拒绝（未评 CRR） |
| N1 / N2 | admin / uw | H 新建页：提示"已有生效承保 v2，请打开后修订"并链接；M 新建页：判定不满足（CRR 未评）→ 人工表单（保存草稿 / 拒绝 / 提交可用，均未点击） |
| N2 只读 | ro | M 新建页无按钮，提示"你可以查看输入，但没有新建承保的权限" |
| uw / lead / ro 详情 | uw / lead / ro | H v2：uw、lead 有"修订"（未点）；ro 打开 L v2 无"修订" |
| E1—E3 入口 | admin | Client 详情 L、已批准入驻案件 L 与 H 的页头均有"承保"按钮 |
| R 菜单资源 | admin | 系统管理 → 菜单资源（只读查看）：`OPERATIONS_RISK_UNDERWRITING` 页面菜单，父级风控管理，页面组件识别为"承保"，可见性权限 `RISK_UNDERWRITING_READ`，路径 `/risk/underwriting`，无不支持告警；按钮 `…_MANAGE`（维护，`RISK_UNDERWRITING_MANAGE`）、`…_APPROVE`（复核，`RISK_UNDERWRITING_APPROVE`）所属页面为承保 |
| G 回归 | admin | 风控规则页、流程实例列表（6 行）、商务协议详情（`/commercial/agreements/240113831739723776`）正常打开 |

- 未翻译 key：0；HTTP ≥ 400：0；写请求：0（每组合 1 次 U11 预览）。
- 控制台：只有 react-router "blocker on a POP navigation" 提示（走查脚本的应用内跳转触发，不是页面问题）。
- 390 横向滚动：承保页面与入口均为 0。

## 3. 未覆盖与说明

| 项 | 说明 |
|---|---|
| 估算敞口公式 | mor-test 的 L、H、M 申请都没有交付方式（存量 / 测试数据），敞口均显示"无法估算 · 未采集交付方式"，公式无法在此出现。公式与实时预览已在阶段 5 本机走查覆盖（USD 35,000.00 = 50,000 × (14 + 7) ÷ 30；T+N 改 7 时 0.00 → 11,666.67） |
| 自动承保判定通过态（原型 02） | L 已承保（新建页按规则跳转或提示已有生效版本），不新建数据；通过态（含"≤ 阈值 USD 50,000"）由阶段 5 覆盖，API 验收 AC-02 PASS |
| lead 打开本人提交记录时按钮禁用 | 当前无待复核记录，按要求不新建；由 API 验收（本人复核 409 SELF_REVIEW，PASS）与阶段 5 走查（四组合按钮禁用并提示）覆盖 |

## 4. 既有问题（非 REQ-03，未改）

| 页面 | 现象 |
|---|---|
| 流程实例列表 `/workflow/instances` | 390 宽度横向滚动 469 px（zh-CN）/ 571 px（en）；REQ-03 未改动该页 |
| 菜单资源 `/console-resources` | en 390 宽度横向滚动 3—27 px（zh-CN 为 0） |
| 菜单资源 | en 界面下资源名称显示 Platform 登记的中文 `display_name`（"承保""维护""复核""风控管理"），与该页其他资源一致（该页按登记名称展示） |

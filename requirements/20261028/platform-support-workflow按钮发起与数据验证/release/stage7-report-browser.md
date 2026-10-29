# REQ-05 阶段 7 · A5 浏览器验收（mor-test，2026-10-29 09:49—10:17Z）

> 执行：wfdq-web。入口 `http://localhost:28080`（运营控制台，同源，普通浏览器行为）；登录凭据由脚本读 `test-environment/private/access.md`，未打印、未落盘。版本：Platform 76717246、ops-bff 6833f484、ops-web 96992aba、ops 语言包 #21。脚本 [acceptance/browser/req05_browser.mortest.mjs](./acceptance/browser/req05_browser.mortest.mjs)（改写自阶段 5 `verification/stage5/stage5_browser.mjs`），ID 取 `acceptance/result-setup-2026-10-29T094655Z.json` 与 `acceptance/result-scenario-2026-10-29T094706Z.json`。

## 1. 对 mor-test 的写操作

| 时间（+04） | 操作 | 结果 |
|---|---|---|
| 13:50 | zh-CN 1440 对 notDrafted 实例 `241498066799116288` 的 N3 动作 `241498066799116291` 点「立即检查」**1 次** | 返回仍未满足 `AGREEMENT_NOT_DRAFTED`，最近检查时间 13:47 → 13:50，页面提示"已检查：仍有 1 项未满足。" |

其余均为只读：未发起、未终止任何实例；流程定义只打开编辑器查看、未保存未发布；动作模板 `REQ05_GOLIVE_DRAFTED` 加一个未声明输出 `client_name` 后点保存——只调用校验接口（返回 `DATA_QUERY_OUTPUT_UNDECLARED`，未发出修改请求；脚本另在浏览器内拦截 PUT 作保险），随后放弃修改。

## 2. 结果（zh-CN / en × 1440 / 390，62 张截图在 `acceptance/browser/screens/`）

| 步骤 | 内容 | 结果 |
|---|---|---|
| 17a / 17b / 17c | 动作目录 `REQ05_GOLIVE_DRAFTED`：模板选择框"生效中 · v1"、数据源"商务（Commercial）从库"、"单次超时 5 秒"；参数 clientId · INTEGER · 必填；输出 3 行（business_mode 枚举 MOR / PSP）；右栏返回约定与执行方式；`client_name` 行标"模板未声明此列 / The template doesn't declare this column" | 4 组合通过 |
| 18a / 18b | 定义 `ACC_REQ05_GOLIVE_COMMERCIAL`（241498022494683136）编辑器：N3 动作卡跟进人、clientId ← 启动表单、输出变量 3 项、系统检查行；网关 g1 出口条件 | 4 组合通过 |
| 19a / 19b / 19c | 我的待办：stuckAtCollection（N8，`CAPABILITY_NOT_ENABLED`）、stuckAtActive（N4）、notDrafted（N3，`AGREEMENT_NOT_DRAFTED`）——"我跟进的检查"、完成标准、还差（中文原因 + 原码）、立即检查、底部自动推进提示；待办列表计数正常 | 4 组合通过 |
| 19d / 19e | 立即检查：按钮加载态"检查中…" → "已检查：仍有 1 项未满足。" | zh-CN 1440（唯一写操作） |
| 20a | 完成实例 `241498065234640896` 活动记录：3 条"提交 · 系统 … 已完成"带"系统"标签，"系统完成 3"筛选，右栏"系统检查 3 已满足 / 0 / 0" | 4 组合通过 |
| 20b / 20c | 实例 `241498066107056128` 节点抽屉：N3 已满足（系统完成时间）；N8 尚未满足 + 立即检查（未点） | 4 组合通过 |
| 90 回归 | 合同详情 `238411681049415680`（DEMO2-CL-103，开通项）、卡 BIN 库 `/card-bin-library`、参考数据 `/reference-data`、对账 `/finance/reconciliation/channel-lines` | 4 组合可打开，无 4xx / 5xx |

- `/api/` 下 HTTP ≥ 400：**0**（4 组合）；控制台 error：**0**。
- 原始 `workflow.*` key：**0**。
- 390 横向滚动：REQ-05 页面 **0**；回归页对账 `/finance/reconciliation/channel-lines` 在 **en 390** 有 27px 横向滚动（筛选表单项 `ant-form-item` 右缘 417px；zh-CN 390 无），属对账页面既有布局，不在本需求范围，按要求只报告不修。

## 3. 执行记录

| 轮次 | 组合 | 说明 |
|---|---|---|
| 1（09:49—09:56Z） | zh-CN 1440（含立即检查）、zh-CN 390、en 1440 完成；en 390 登录页停留后超时退出 | 同一账号短时间第 4 次登录未进入控制台（推断为 Gateway 登录限流），结果 JSON 未写出；日志 `acceptance/browser/run-first.log` |
| 2 | en 390 | 等待 90 秒后单独重跑，通过；`browser-result-en-390.json`、`run-en390.log` |
| 3 | zh-CN 1440、zh-CN 390、en 1440（只读，不点立即检查） | 脚本加登录重试（状态码记录、等待 45 秒后重试，最多 4 次）后重跑，3 次登录均首次 200；补全控制台与 HTTP 记录；`browser-result-zh-CN-1440_zh-CN-390_en-1440.json`、`run-readonly.log`。19d / 19e 截图保留第 1 轮 |

中间还有一次只读重跑因同样的登录问题在 en 1440 退出（未产生写操作），结果被第 3 轮覆盖。

## 4. 与原型 17—20 的差异

同阶段 5（`verification/stage5-report.md` 8.4）：17 无"模板 N 秒，取小值"实例（模板均 5 秒）；18 流程级问题只在保存后由服务端给出、网关条件显示输出名、连线为条件文字；19 无"由立即检查触发 · 开始时间"、状态标签行头与检查块各一次、列表图标为填写类；20 无"本节点的检查（已结束）"列表。mor-test 未发现新的页面缺陷。

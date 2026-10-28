
## Operations

本机 e2e（https://127.0.0.1:18444，运营 Web 为 Code-MoR-legal 工作树 Vite 热更新；client-master、运营 BFF 为本需求构建），无头 Playwright，2026-10-28。脚本 `ops_legal.mjs`（管理员凭据取 `local-environment/e2e/secrets.env`，不打印）；截图 `ops-screens/`；逐项记录 `ops-findings-<lang>-<width>.jsonl`。

案件：v2 新案件 `240934496831033344`（申请 240933106532495360，AE 机构，client-fe 提交）；存量案件 `240814816938184704`（v1 申请，6 项旧声明含 PLATFORM_TERMS）。

| 项 | en 1440 | en 390 | zh-CN 1440 / 390 |
|---|---|---|---|
| 1 入驻配置 AE / SA / EG：DECLARATION / ATTRIBUTE / 门槛比较方式规则行带文案；开关规则显示是 / 否，数值门槛保持数字；Schema v2 生效、v1 已下线（SA 自由职业者 v1 生效） | 通过 | 通过 | 布局通过；新文案键显示英文（延后，见下） |
| 2 v2 案件声明区块：取值为是 / 否文字、声明时间、声明账号、条款版本 2026-10-draft、"Matches submitted data"；ERN、登记机关名称（Other）字段带文案；无 true / false | 通过 | 通过 | 布局通过；新文案键显示英文（延后） |
| 3 存量案件：旧声明（PLATFORM_TERMS 等）带文案、条款版本"—"、状态"Matches submitted data"（存量资料版本按 0） | 通过 | 通过 | 布局通过；新文案键显示英文（延后） |

检查数：en 1440 68/68、en 390 70/70；zh-CN 1440 51/68、zh-CN 390 53/70，17 项失败全部是本需求新文案键未进入已发布的 zh-CN 语言包（运行时回落内置英文，符合设计）；已有中文文案、无横向滚动、无原始文案键、无 true / false 均通过。按负责人决定，新键的中文在阶段 7 mor-test 发布语言包后验证，本机不发布。

浏览器验收中修正的 Web 问题（运营 Web 测试 1572/1572、构建通过）：
- 声明表文本列被挤窄：声明时间与账号合并为"Declared"一列。
- 390 宽度案件页整页横向滚动 303px（既有分区页签条）：页签条改为在自身范围内滚动。
- 配置页 Schema 下拉默认打开 v1 已下线版本：改为默认打开启用版本。
- 规则取值 PARTY_TYPE、ACQUIRE_MODE、UBO_FALLBACK_RULE、UBO_COLLECTION_MODE 显示为文案。
- 申请资料布尔值（如"上市公司"）显示 false：改为是 / 否（审核页与 Client 详情共用的取值显示）。

未处理：各页面存在 antd 弃用告警（Spin `tip`、Space `direction`），与本需求无关。

## Client

本机 e2e（https://127.0.0.1:18443，Client Web 为 Code-MoR-legal 工作树 Vite 热更新；client-master、Client BFF 为本需求构建），无头 Playwright，2026-10-28。

- 账号：`register.py` 经 Client 入口注册 5 个 `legal-ui-*@example.test` 测试账号，Mailpit 收不到验证邮件，所以沿用 V2 公共件 `mint_signup_link` 在本机库为本账号铸造验证秘密。凭据只存 scratchpad 的 0600 文件。
- 脚本：`lib.mjs`（扫描错误横幅、原始键、横向溢出）；`flow.mjs`（切片 1 流程改写，新增参考数据下拉与新字段取值）；`case1.mjs`、`cases.mjs`、`views.mjs`、`probe.mjs`。
- 证据：截图 `screens/`；结果 `case*.json`、`views-*.json`；声明请求体 `requests-*.json`；响应 `bodies/`。

| 项 | 申请 | 结果 | 证据 |
|---|---|---|---|
| 1 AE 机构（en 1440） | 240933106532495360，已提交、审核中 | 通过。v2 表单；ERN 为可选；登记机关选 Other 后出现"Registration Authority Name"，必填（空时提示 Fill in this field）。声明 8 项，条款与隐私各有 Read 面板（Draft 标识、Version 2026-10-draft、"I agree"），勾选时带 `termsVersion: "2026-10-draft"`，其他声明不带。勾选后修改资料：8 项全部失效（`current=false`，需求为 `DECLARATION_STALE` / `SIGN_DECLARATION`），概览显示"0 of 8 confirmed · Needs attention"，核对并提交锁定。重新勾选后提交成功，结果页为 In review。 | `screens/C1-AEO-en-1440-*`、`case1-AEO-en-1440.json`、`requests-AEO-en-1440-AE-O.json` |
| 2 SA 机构（zh-CN 1440） | 240935015863562240 | 通过。无执照到期日；有"CR Annual Confirmation Date"（可选）。人员说明与检查为"25% 及以上"。 | `screens/sa-org-zh-CN-1440-*`、`screens/V-SAO-*`、`case-sa-org-zh-CN-1440.json` |
| 3 SA 个人自由职业者（en 390） | 240935015867756544 | 通过。AB123 报"The format doesn't match"，FL-ABC123 通过。到期日 2020-01-01 保存后由服务端拒绝，字段显示"The document has expired."，个人资料任务为 Needs attention；改为 2028-10-26 后恢复。 | `screens/sa-fl-en-390-*`、`case-sa-fl-en-390.json` |
| 4 EG 机构（zh-CN 390） | 240937710473195530 | 通过。登记号为统一 / 本地商业登记号二选一，同一时间只有一个号码输入；增值税登记号与税卡号为单独字段。 | `screens/eg-org-zh-CN-390-*`、`case-eg-org-zh-CN-390.json` |
| 5 AE 个人独资（en 1440） | 240937710473195520 | 通过。发证机关选 Other 后出现"Licensing Authority Name"，必填；ERN 为可选。 | `screens/ae-ind-en-1440-*`、`case-ae-ind-en-1440.json` |
| 6 AC-10 v1 草稿 | — | 未在浏览器验证（负责人决定：不在共享本机库写凭据或重置秘密）。由 client-master 基于 v1 夹具的自动测试覆盖（存量声明的资料版本、退回后重提）。本机库有 3 个 v1 草稿（AE 240791861415915520，EG 240791298657759232、240790732317667328），都是 DRAFT，没有案件，运营案件读取路径不涉及。v1 存量案件的运营只读展示见上文 Operations 项 3（案件 240814816938184704）。 | — |
| AC-V1 视觉矩阵 | SAI、SAO | 通过。en / zh-CN × 1440 / 390 下声明页（含失效）、阅读面板、概览、人员页均无横向溢出、无原始键、无错误横幅。zh-CN 下本需求新键显示内置英文，原因是本机未发布语言包，属预期，阶段 7 验证。 | `screens/V-*`、`views-*.json` |

浏览器验收中修正的 Web 问题（Client Web 测试 141/141、构建通过）：

- 失效提示重复：每行都显示整句"你在确认后修改了资料……"，8 项时噪声大。改为页顶一条汇总（"Confirm the N marked declarations again"）加行内短标记"需重新确认"；人员页的确认项仍显示整句。
- 证件多选时号码标签重复："Unified Commercial Registration Number number"（中文"统一商业登记号号码"）。类型名本身以 Number、号、号码或编号结尾时，直接用类型名。此前的"营业执照号号码"也一并修正。

说明 / 未处理：

- 自由职业文件到期日"不能早于今天"只由服务端判定（规则不随 Schema 下发），所以保存后才显示错误，不在输入时提示。
- AC-07 中"未重新确认即提交返回字段错误"：页面在声明失效时锁定提交入口，浏览器触发不到服务端拒绝，服务端行为由 client-master 自动测试覆盖。需求清单返回 `DECLARATION_STALE` 已在响应中核对（`bodies/C1-*`）。
- 本机所有门槛均为 GTE，"超过 {n}%"（GT）文案只由单元测试覆盖。

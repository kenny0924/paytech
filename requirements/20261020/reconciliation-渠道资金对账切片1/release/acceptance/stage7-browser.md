# 阶段 7 浏览器只读验收（mor-test）

- 时间：2026-10-21；入口 `http://localhost:28080`（Nginx → Gateway → Operations BFF）；无头 Chromium，Operations 管理员登录（口令运行时读取 `test-environment/private/access.md`，未打印、未落盘）。
- 脚本：`release/acceptance/stage7_browser.py`（`python3 -I stage7_browser.py`）；机器可读结果：`release/acceptance/stage7-browser-result.json`。
- 只读：只做菜单/列表查看、点击行首单元格打开详情抽屉后按 Esc 关闭、执行在途汇总「查询」（GET）。没有点击新建、提交、批准、作废、删除或上传按钮，也没有修改数据、容器或数据库。

## 结果：27/27 通过（登录 1 + 对账 7 + 回归 19）

### 财务 → 对账（7 页）

| 页面 | 结果 | 观察 |
|---|---|---|
| 清算账户 | 通过 | 列表 2 行；详情抽屉可打开 |
| 银行账户 | 通过 | 列表 2 行（尾号 4321 有效、9999 已关闭，只显示末四位）；详情抽屉可打开 |
| 渠道账单 | 通过 | 列表 3 行；详情抽屉显示 5 条门禁项：✗ 交易行都已匹配到平台流水 / ✓ 调整行与无交易号费用行都已归类 / ✗ 没有阻塞对平的开放异常 / ✓ 各行回放与账单头合计一致 / ✗ 清算账户已配置到账银行账户 |
| 平台流水 | 通过 | 列表 2 行（PAYMENT 100.00、REFUND 30.00，已结算）；页面设计上没有详情抽屉（`ReconPlatformTxns.tsx` 中没有 Drawer） |
| 银行对账 | 通过 | 下拉框只能选有效账户（尾号 4321）；银行流水、对账单、预期银行流水三个页签都能正常显示，均为 0 行，说明当前合成数据没有银行侧记录；空状态提示文字正常 |
| 对账异常 | 通过 | 列表 1 行，有「账龄」列（0 天）；详情抽屉显示状态、对账层、差异金额、来源对象和处置记录（只看，没有提交） |
| 在途汇总 | 通过 | 默认币种 USD，点「查询」后显示 4 行汇总 |

### 回归（ops-bff / ops-web / platform 镜像已替换）

| 分组 | 页面 | 结果 |
|---|---|---|
| 商务（9 个路由，菜单为工作台 + 8 个） | agreements、pricing-profiles、terms、templates、workbench、rate-cards、price-simulator、catalog、history | 全部通过，没有错误提示 |
| 财务原有 7 页 | setup、ledger、balances、subledger、adjustments、period-close、accounting-rules | 全部通过，没有错误提示 |
| 渠道 3 页 | channels、mids、cost-plans | 全部通过，没有错误提示 |

错误提示的判定范围：`.ant-alert-error`、`.ant-result-error/500/403/404`、`.ant-message-error`、`.ant-notification-notice-error`，以及「页面不存在 / 无权访问 / 404」文字。

### 控制台与网络

- 控制台 error：0 条；warning：0 条。本次运行没有出现 App.tsx 里已知的 Spin tip 弃用警告（0 条）。
- 4xx/5xx 响应和失败请求：0 条。

### 截图（`release/acceptance/screenshots/`）

- 对账：r01-clearing-accounts、r01-clearing-accounts-detail、r02-cash-accounts、r02-cash-accounts-detail、r03-statements、r03-statements-detail、r04-platform-txns、r05-bank、r05-bank-lines、r05-bank-statements、r05-bank-movements、r06-exceptions、r06-exceptions-detail、r07-summary
- 商务：c01-agreements … c09-history（9 张）
- 财务：f01-setup … f07-accounting-rules（7 张）
- 渠道：ch01-channels、ch02-mids、ch03-cost-plans
- 共 33 张 PNG

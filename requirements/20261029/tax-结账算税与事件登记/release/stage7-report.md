# 阶段 7 报告：tax 发布 mor-test

- 日期：2026-10-30（UTC 03:36—06:05）
- 授权：用户"继续"（确认阶段 6，D-1—D-5 按建议）、"执行"（mor_tax 重建被自动模式拦截后的明确授权）。
- 执行：主会话按 [runbook](./runbook.md) ①—⑭ 顺序执行；备份目录 `~/Desktop/Workspace/Code-MoR-backups/tax-20261029/mortest-20261030T033945Z`。
- 候选源码：main `30361be7` + tax 改动 379 个文件（`/Users/kenny/Desktop/Workspace/Code-MoR-tax-rel`，**未提交**）；研发 worktree `/Users/kenny/Desktop/Workspace/Code-MoR-tax`。

## 1. 结论

发布完成，6 个服务健康，验收 A1—A4、A7 通过；A5、A6 不适用（见第 3 节）。未回滚任何一步。

| 服务 | 镜像（`tax-20261029a`） | 启动（UTC） |
|---|---|---|
| tax-service | `sha256:a5616e20b18c04e2e5bb158031898f8c7bef9ab26540d5a025d5be3975cf1c31` | 05:43:41 |
| billing-service | `sha256:adfd081728ae50759ba9dc2ce18aa9a019c0097997912925c7c19b7c75dfc05e` | 05:46:02 |
| account-service | `sha256:8a21dab8e54bde0945e52dcace5a4a2722a581dbba79d7b1e4d8acc257e2b68e` | 05:46:40 |
| acquiring-service | `sha256:843382e35d401341a8984bcbc02ceb98b7c9774d4b48524fe602e44058bed01c` | 05:47:33 |
| operations-console-bff | `sha256:e243d57bd5406a2ac8efa133b52586cc38acfd60ec2c739e6fe5ff1dabd6fe27` | 05:48:10 |
| operations-console-web | `sha256:748ca7b9c6b417104bbb7e3a09ba4b2630d8c4c513a4de2c43644a769b66a97a` | 05:49:01 |

Platform、ledger、payment 不随发（D-3 与阶段 6 报告）。

## 2. 执行记录

| 步骤 | 结果 |
|---|---|
| ① 预检 | 源码漂移、env、镜像核对全部一致；已通知 design-discuss-b8 / design-discuss-19。发布前基线订单首次 503（risk-decision 评估超时，冷启动，与本需求无关），重试 201 FINAL 零税 |
| ② 备份 | 涉及库与 compose / 镜像记录全部备份 |
| ③ 只读预检 | 通过；唯一差异为本次基线订单 |
| ④ 块 146 休眠写入 | routeCount 保持 631 |
| ⑤ mor_tax 重建 + 初始化 | 16 张表；AE001 Party `880000000702`（首次被自动模式拦截，用户"执行"后完成） |
| ⑥ tax-service | 健康；`tax.acquiring.p0—p3` 均有消费者 |
| ⑦ XXL | 执行器分组 id 18、任务 `compensationEventScan` id 28；执行器注册后手动触发 200（XXL Admin 05:11 曾被重建，健康） |
| ⑧ billing | 注释 DDL 已执行，新镜像上线 |
| ⑨ account | 新镜像上线 |
| ⑩ acquiring | 停机迁移（存量 52 笔 FINAL 订单回填），新镜像上线 |
| ⑪ ops-bff | 新镜像上线 |
| ⑫ 块 146 激活 | routeCount 631 → 633；税务路由未登录返回 401，与参照路由一致 |
| ⑬ ops-web | 新镜像上线，dist 266 个文件 |
| ⑭ 语言包 | operations-console **#24** READY（10 902 key，+214）。发布调用经 Gateway 返回 504，但发布已完成，未重复发布 |

## 3. 验收

| 项 | 结果 | 说明 |
|---|---|---|
| A1 preflight | PASS | — |
| A2 发布后影响 | PASS（第二次） | 美国 + STANDARD → BLOCKED `MARKET_NOT_ENABLED`；AE + STANDARD → BLOCKED `UNKNOWN_TAX_CATEGORY`；发布前基线订单 FINAL、tax 查询 404；存量已付订单 FINAL 零税。首次运行 FAIL 发生在重启后第一次建单，为 risk 冷启动超时，18 秒后重跑通过 |
| A3 场景 | PASS | S1 沙特：建单暂估 AE 5%（105.00）→ 资料提交 SA 最终 15%（115.00）、可付；S2 科威特 BLOCKED、付款 409 |
| A7 合成事件 | PASS | 订单 `241801063336886272`（S1），`TAX_SYN_CONSUMER_COUNTRY=AE`（SA 渠道成本无匹配）。tax 交易 PENDING `FX_RATE_MISSING`、税票 `SA-RCPT-2026-000001` ISSUED；billing PAYMENT 事实 `241801538719309824` 含 `SELLER_TAX` 15.00 → TAX_AUTHORITY `880000000702`、本金 96.59；account 已入账；ledger `TAX_PAYABLE` 贷记 15.00；死信 0。检查脚本修正一处：单据嵌套在 `transactions[].documents` 下（`acceptance/tax_api.py` 约第 491 行） |
| A4 浏览器 | PASS | zh-CN / en × 1440 / 390，结果 `acceptance/browser-result-2026-10-30T060210963Z.json`，截图 `acceptance/screens/`。无原始 i18n key、无横向溢出；HTTP 错误只有预期 404 与浏览器侧模拟的下载 503 |
| A5 / A6 | 不适用 | mor-test 无可用卡渠道与真实付款路径（阶段 6 F-7），NOT_MANAGED 来源订单无法在 MOCK 模式造出 |

浏览器回归说明：工作流定义页为卡片布局，脚本按表格行计数得 0，截图显示 8 个流程正常；承保页 390 宽度截图时列表仍在加载（1440 宽度 3 行），均非回归。税票下载失败提示在切换页面后保留到超时，为 antd 通知默认行为。

## 4. 已知不一致与遗留

- **验收数据跨系统不一致（R-5）：** S1 订单在 tax / billing / account / ledger 已按合成事件记为已付，在 acquiring 仍为未付，到期后 EXPIRED。合成支付 ID 在 `9999000000000003xx` 段，便于识别。
- **D-1 生效：** mor-test 上非 AE / SA / EG 或税类非 `SAAS_AUTOMATED` 的 MoR 订单一律 BLOCKED、不可付；其他会话脚本需在下次使用时改为 AE / SA / EG + `SAAS_AUTOMATED`（文件清单见阶段 6 报告 F-8）。
- **语言包发布 504：** Gateway 超时早于 Platform 完成发布，属既有现象，未处理。
- **代码提交：** 阶段 8 已本地提交 `fa21d5ce`（父提交 `30361be7`，内容 = 候选 worktree = 现网镜像源码），未推送。
- 阿拉伯语税票（方案 A，待补）、汇率源（D-01，待补）、tax 上传失败日志无原因（阶段 5 遗留）。

## 5. 回滚

按 [runbook](./runbook.md) 第 5 节逆序执行。acquiring 已有新单，迁移回滚会被拒绝（设计如此），只能前滚修复；tax 回滚镜像为重建的 `884728dc…`（D-4）。

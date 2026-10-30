# REQ-04 阶段 7 · mor-test 浏览器走查（⑮ 第 5 步，rq04-web，2026-10-30 +04）

目标 `http://localhost:28080`（mor-test 运营入口，语言包同源）。版本：ops-web 2f7f8780、ops-bff 4bd4c3df、Platform af13e77a、gateway d51daee3，块 145，operations-console 语言包 #28，档位流程 ACTIVE。
**只读**：除登录外没有任何写请求（脚本统计非 GET 的 `/api/` 请求，四组合均为 0）；未打开申请弹窗、未审批。账号 `~/Code-MoR-backups/rq04-stage7/accounts.json`（req / appr / elev / ro），凭据未打印。

方法：Playwright 无头 Chromium，[stage7_browser.mjs](./stage7_browser.mjs)；zh-CN / en × 1440 / 390，每组合 4 个账号、18 步（共 72 步、72 张截图 `screens/`），结果 `result-<组合>.json`，日志 `logs/run-all.log`。每账号每组合登录一次，应用内跳转；组合间隔 75 秒，登录按退避重试（本次 0 次重试）。

| # | 检查项 | 结果 | 依据 |
|---|---|---|---|
| 1 | 菜单"风控管理 → 限额档位"可见 | PASS | 1440 两语言四账号：侧栏"风控管理 / 限额档位"、"Risk / Limit tiers"；390 侧栏收起，页面经同一路由打开 |
| 2 | 列表统计 4 STANDARD / 2 VERIFICATION | PASS | 统计卡 验证 2、观察 0、到期 0、标准 4、受限 0、待审批 0；共 6 条 |
| 3 | 列表筛选 | PASS | 标准档页签 4 行、验证档页签 2 行（L、H）、只看到期待确认 / 只看待审批为空状态、按 Client ID 搜索 1 行；行操作：req 对验证档为"申请上线"，其他账号均为"查看" |
| 4 | L 档位页（VERIFICATION、额度与已用、清单开通项未满足） | PASS | L 240686705999454209：验证档、CRR LOW；额度 AED / EGP / SAR / USD 四币种，已用 AED 100、USD 100（当日与近 30 天）；清单资格、承保 OK，合同、支付方式开通项"未满足"，"提交申请"禁用；历史 3 条（承保建档 → 受限往返） |
| 5 | H 审批人视角 | PASS（只读） | H 241049681294106625：验证档、CRR HIGH；appr / elev 无写按钮（无申请权限），清单只读；历史 3 条含"已驳回（意见）"与"已撤回"。当前无待审批申请，审批区不出现；待审批审批人视角（普通 / 高级 / 本人）已在阶段 5 本机走查覆盖 |
| 6 | 历史时间线 | PASS | L、H 各 3 条、迁移 Client 1 条（存量迁移 · 已生效）；申请 / 审批 / 驳回 / 撤回人显示为掩码邮箱，理由与意见完整 |
| 7 | RO 无写按钮 | PASS | ro 在列表与 L、H 页 `.uw-page button` 为 0（只有说明"你可以查看档位，但没有申请变更的权限"） |
| 8 | zh-CN 无原始 key / 英文泄漏 | PASS | 原始 key 0；界面文案位置的英文只有业务数据（客户名、掩码邮箱、`CL-` 编号、验收理由中的 REQ-04 / mor-test）与清单来源标签 compliance / risk / commercial（同原型 02，系统名不翻译） |
| 9 | 390 无横向滚动 | PASS | 四组合 72 步 `scrollWidth - clientWidth = 0` |
| 10 | HTTP ≥ 400 / 控制台 | PASS | HTTP ≥ 400：0；控制台只有走查脚本 `pushState` 跳转引起的 react-router 提示 |

P0 / P1 缺陷：无。

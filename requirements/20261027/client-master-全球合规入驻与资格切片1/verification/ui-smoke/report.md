# mor-test 浏览器冒烟（ui-smoke，2026-10-27）

最终镜像：client-console-web 34d3d6e3、client-master 0af5b792、operations-console-web 88ab111a。证据：本目录 `screens/`、`bodies/`、`ops-findings-*.json`、`client-findings-*.jsonl`；脚本 `ops_smoke.mjs`、`ops_issue_code.mjs`、`client_lib.mjs`、`client_flow.mjs`。（报告由主会话按 ui-smoke 最终消息整理。）

## 端到端结果

- AE 机构申请 240723389373849600（入驻码 ****-ZYXP）：公司资料、地址、人员（含 GB 地址人员，抽屉切换 GENERIC 布局）、公司材料 8 份、人员材料 2 份、声明 6 条，en / 1440 提交；审核中页正常；运营案件 240726225734193152 中英文正常（名称、国家、PayFac 名称已翻译）。
- AE 个人申请 240719914258767872：zh-CN / 390 提交；运营案件 240721695093149696 可打开。
- 草稿撤回：1440（zh-CN）与 390（zh-CN，两次）通过；14690d94 起 390 宽度 26 个视图（中英文）无横向溢出。运营 88ab111a 中英文全页面与 1274fefe 第二批复测 0 问题。

## 发现与处理

| # | 级别 | 页面 | 现象 | 根因 | 处理 | 状态 |
|---|---|---|---|---|---|---|
| 1 | P0 | 运营案件工作台 | 新案件全部 INVALID_RESPONSE | 解码器要求 fileId / label，BFF 返回 fileAddress / labelKey | 解码 fileAddress、labelKey | 已修 35b61f12，已验 |
| 2 | P0 | 客户地址任务 | 地址 PATCH 全部 422，数据静默丢失 | 前端送 `address.addressFormatCode`，client-master 可写清单缺该键 | 前端不送 + 服务端容忍 | 已修 4aaecccb / ba3f136f，已验 |
| 3 | P0 | 客户人员抽屉 | 添加人员 422（无 errors[]） | client-master 要求 relation.effectiveFrom，前端未送 | 前端送当天；服务端缺省当天并返回 errors[] | 已修 34d3d6e3 / 0af5b792，已验 |
| 4 | P1 | 客户各任务 | 一次失败的保存污染后续保存 | 失败字段反复重发 | 422 字段隔离 | 已修 14690d94，已验 |
| 5 | P1 | 客户 390px | 顶栏溢出 64 / 35px | 审核方名称未截断 | 截断 | 已修，已验 |
| 6 | P1 | 客户 390px 草稿首页 | 撤回不可达 | 侧栏在窄屏隐藏 | 移动端撤回入口 | 已修，已验 |
| 7 | P2 | 客户中文列表 | 无分隔符 | ListFormat unit | conjunction | 已修，已验 |
| 8 | P2 | 客户材料 | 检查中继续导致上传未挂接 | 只在槽位重新挂载时续接 | 禁止继续 + 后台续接 | 已修（未再触发） |
| 9 | P2 | 客户错误提示 | 英文界面显示中文服务端文案；抽屉原始键 | 直接打印 detail | 按错误码本地化、标签 | 已修，已验 |
| 10 | P2 | 客户自动保存 | 并发 PATCH 429 | 无串行化 | 串行队列 + 防抖 + 退避 | 已修，已验 |
| 11 | P2 | 客户已撤回 / 审核中 | 无撤回时间 / 提交时间 | C05 无 submittedAt / withdrawnAt | 代码冻结，列后续 | 未修（后续） |
| 12 | P2 | 客户人员抽屉 | 无字段时提示「检查高亮字段」 | 关系校验无 errors[] | 服务端返回 errors[] + 前端通用提示 | 已修 0af5b792 / 34d3d6e3 |
| 13 | P2 | 运营入驻码 | 过期码显示有效 | 无时间推导 | 前端推导 EXPIRED | 已修 1274fefe，已验 |
| 14 | P2 | 运营 Client 详情 / 工作台 / 配置 | 原始目录码、PayFac ID、国家码、网格逐字折行、Schema 标签 | 缺翻译 / 布局 | 映射 + 布局 | 已修 1274fefe / 88ab111a，已验 |
| 15 | P3 | 运营工作台申请资料 | 「上市公司: false」 | 布尔值未渲染为是 / 否 | — | 未修（后续） |
| 16 | 信息 | 运营历史轮次案件 | 申请资料不可用 | client-master 只返回当前轮次 | 说明 + 当前轮次链接 | 已修 1274fefe |
| 17 | 信息 | 环境 | 注册验证邮件无法在 mor-test 验证 | 邮件走真实 SES（2026-10-15 环境配置） | — | 未覆盖 |
| 18 | 信息 | 客户 / 运营 | 偶发 503（client-master 重部署窗口内） | 瞬时 | — | 未复现 |

未覆盖：SA / EG 机构经页面提交（由接口验收覆盖）；mor-test 上的 RETURNED 页面（需运营退回操作）。

## mor-test 留存测试数据

ui-smoke 账号：AE 个人案件 240721695093149696 SUBMITTED，SA 草稿 240717856545808384 WITHDRAWN；ui-smoke-org 账号：AE 机构案件 240726225734193152 SUBMITTED，2 个草稿 WITHDRAWN；入驻码 ****-ZYXP（3 次已用 2）；未验证注册 ui-smoke-{A,B,C}-1027060108@example.test（无数据）。两个已验证账号由 accept-dev 以 `mint_signup_link` 只改其自身注册挑战（主会话批准，凭据仅存 `~/Code-MoR-backups/onboarding-slice1-acceptance`，0600）。

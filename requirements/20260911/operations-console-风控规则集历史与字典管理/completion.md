# 风控规则集历史与字典管理：验收归档

2026-09-12 用户明确“我验收了，代码提交吧”，确认 test 验收并授权本地 Git 提交。提交到现有 main 分支；本期包含 Risk、Operations BFF/Web、Gateway、Platform 五个应用仓库及 Code-MoR 根仓库。

本期交付历史版本与当前详情的只读编辑器、发布引用关系、拒绝原因和主体标签字典；新增 14 个 API，保留 v1，新增 2 表/21 列。Gateway 的业务 Target/Route 从数据库快照读取，BFF 管理监听独立。Web 最后反馈包含紧凑规则卡片摘要、移除页面返回按钮及保留浏览器未保存保护。

## 验收与提交证据

- 用户负责 test 功能/视觉验收，本记录依据用户确认，不将本地验证替代 test 验收。
- 现有验证源码 Hash 与本次应用改动一致：Risk 186 项、BFF 1041 项、Gateway 1199 项、Platform 386 项；Web 先前全量 883 项，最后反馈相关 69 项及 TypeScript/Vite 构建通过。完整边界和历史检查点见 [delivery.md](./delivery.md)。
- Pencil 文件仅新增本期 12 个画板，原画板保留；最新 Web 反馈设计取代范围见 [ui.md 第 8 节](./ui.md#8-2026-09-12-人工验收后的-web-调整)。
- 本次需求站点构建及 136/136 项检查通过；首轮 1 条旧“未部署”断言已按实际部署记录修正。应用 Git 提交清单见 [stage8-commits.json](./assets/stage8-commits.json)。根仓库提交为包含该归档清单的提交。
- 已部署制品、SQL 检查点和示例数据保持原有记录；当前实施状态以 [CURRENT_IMPLEMENTATION.md 第 17 节](../../../agent-docs/CURRENT_IMPLEMENTATION.md#17-operations-规则历史与业务字典2026-09-11) 为准。

本次不推送、不创建分支或 PR，不再部署或执行 SQL。此前公共流程草稿及上一需求未提交材料继续保留在根工作区。

## 应用提交

| 仓库 | main 提交 |
|---|---|
| risk-compliance-service | `27cf50b6fc48` |
| operations-console-bff | `6822928fc355` |
| operations-console-web | `9165b95e76da` |
| api-gateway | `2f1f70e85e1a` |
| platform-support-service | `d40b268b6440` |

根仓库为包含本文件和提交清单的提交。五个应用工作区均已核对干净；未执行推送。

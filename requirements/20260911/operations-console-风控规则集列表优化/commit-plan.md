# 规则集列表优化：待提交清单

本文保留阶段 7 交付时的待审阅范围；用户后续已授权本地提交，执行结果见 [阶段 8 归档](./completion.md)。以下“尚未暂存/提交”及文件数量属于阶段 7 准备记录。用户确认此清单并进入阶段 8 后，才按独立仓库做本地提交与交付归档；不创建分支/PR、不推送、不重新部署或发布制品。

| 仓库 | 待审阅路径数 | 拟提交内容 |
|---|---|---|
| Code-MoR | 78 | 记录规则集列表优化及 test 发布验收 |
| risk-compliance-service | 60 | 支持规则集整体管理与删除 |
| operations-console-bff | 16 | 转发规则集删除并保持原 v1 合同 |
| operations-console-web | 20 | 优化规则集列表操作与浏览器导航 |
| paytech-foundation | 3 | 延迟资源锁监控注册器初始化 |
| platform-support-service | 1 | 注册 Operations 规则集删除权限和路由 |

逐文件路径、当前 HEAD 和五个应用/组件仓库的工作区 SHA-256 见 [机器可读清单](./assets/stage7-pending-files.json)。路径数包含新增/删除及需部分选取的文件，不表示已经暂存。根仓库的交付文档允许在归档时补齐本轮提交号，提交前仍需核对最终差异。

- **Code-MoR：** 本需求目录（评审、方案、SQL、阶段验收和脱敏证据）、Risk 模型生成脚本及模型/场景门禁、全局 Risk 用例/时序与对应说明、test Risk Redis 配置及离线配置检查、当前状态台账，以及本轮确认的 API 默认版本/类型命名决策与薄投影。
- **Risk：** API/DTO 去版本后缀、RuleSet 根及持久化、整体删除和错误/幂等/并发保护、Processor/Repository/Service/HTTP 接入、PDManer/Schema 和相关测试；包括 Converter 及测试文件重命名。
- **BFF：** 列表响应合同、删除权限/错误映射及一次 Owner 转发，配套严格输入/响应校验与测试。
- **Web：** 列表单行/精简、编码校验、详情/编辑/删除菜单、URL 历史、过滤/分页与未保存保护及相关测试。
- **Foundation：** 仅 Redis Starter 的两份实现及一份监控初始化回归测试。
- **Platform：** 仅单一初始化 DML 的 16 行删除能力注册及角色版本更新；默认 Route/Resource 为 DISABLED，已执行的 test 曝光 SQL 位于本需求目录。

## 需部分选取的内容

`agent-docs/README.md` 只选本轮 API 命名入口和规则集列表方案路由；`agent-docs/DOCUMENT_GOVERNANCE.md` 只选规则集整体管理 Owner 及本轮命名示例。此前公共流程、需求目录治理的修改保留在工作区。

Risk 的 `DecisionRuleSetServiceImpl.java` 与 `DecisionRuleSetManagementController.java` 在本需求开始前已有 import 整理。提交时精确区分本轮业务/类型改动与原有纯整理；重叠处单独构造暂存内容，保留工作区文件，不通过重置用户文件处理。

## 排除项与验证对应

排除根 `AGENTS.md` 的先前工作流修改、公共流程文档与规则、`requirements/README.md`、需求模板、上一项“风控规则改动”归档和正在进行的 CCT 模型需求；这些不是本轮代码提交范围。Gateway 没有本轮代码修改，无需空提交。详细排除目录见机器清单。

不收录账号凭据/Token、private 环境材料、数据库备份、临时验收脚本、target/dist/node_modules、镜像或构建制品。执行前再次核对是否出现其他任务变更；不使用无范围的 `git add .`。

验证沿用 [阶段 4](./assets/stage4-verification.json)、[阶段 5](./assets/stage5-verification.json)、[阶段 6](./assets/stage6-release-package.json)与[阶段 7 发布验收](./deployment.md)中对应范围。阶段 7 没有修改业务代码；根文档门禁与静态核对见 [阶段 7 静态检查](./assets/stage7-static.json)。本地提交后记录每仓库提交号，推送状态单独保留为未执行。

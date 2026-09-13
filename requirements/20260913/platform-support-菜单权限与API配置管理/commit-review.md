# 本地提交范围

用户在 test 发布及 Web 样式反馈修复后明确要求“代码提交”，据此进入本地 Git 提交归档。本文记录本次提交范围，不把该要求扩大为推送、PR 或其他环境操作授权，也不额外推定用户已完成全部业务验收。

| 仓库 | 本需求拟提交内容 | 建议摘要 |
|---|---|---|
| platform-support-service | 配置管理 API、资源/权限/Target/Route 实现、A18 授权修复、审计/并发保护及相关测试；完整初始化中本期 227 条禁用注册；本次发布所必需的三个 CHANNEL_CALLBACK 公共枚举及相关校验 | feat(platform): manage console resources, permissions and API configuration |
| operations-console-bff | 三类配置管理转发、模型/权限/校验及测试；通用 ConfigurationValidationConverter 和相应 Risk 调用方的必要引用调整 | feat(operations): forward platform configuration management APIs |
| operations-console-web | 菜单树、权限目录、Target/Route 配置页面、详情/编辑/返回交互、公共组件、页面能力和本期 Pencil 设计 | feat(operations): add platform configuration management pages |
| paytech-foundation | 仅 HttpServiceClientInterceptor 与 HttpServiceClientFactoryTest 的合法 404 映射修复 | fix(http): preserve downstream not-found problems |
| api-gateway | 本轮明确批准随 test 发布的 CHANNEL_CALLBACK 命名兼容：三个生产类及两个测试文件，无其他网关功能 | fix(gateway): align callback channel configuration codes |
| 根工作区 | 本需求目录、当前台账的本需求条目、需求目录/时序中 PLT-16/PLT-17 的本期相关改动；按具体差异挑选 | docs(requirements): record platform configuration delivery |

[工作区文件观察](./assets/stage7/working-tree-paths.json)提供当前五个应用仓库的 dirty 路径入口；它含共享工作区观察结果，不能直接作为批量 git add 清单。提交前逐项审阅实际 diff，并保留其他任务改动。

明确排除 Payment/Finance/Risk 模型与工程任务、其他 Channel 专项的数据库模型/Schema/物理设计变更，以及无关的根文档。Platform 的 platform-support.pdma.json、platform-support-schema.sql、PLATFORM_SUPPORT_PHYSICAL_DECISIONS.md 属于其他专题，本需求没有表结构修改。共享的初始化 SQL 和当前台账只挑选本期相关差异，不覆盖整个文件。

任何真实凭据、test-environment/private、数据库备份、Maven target、Web dist、Docker 构建上下文、临时浏览器状态和敏感日志均不进入提交。允许入库的发布证据只保留非敏感元数据、已评审 SQL、界面截图和 Hash。

提交前检查暂存区并核对制品对应差异，只提交上述需求相关内容。推送、PR 与远程制品发布不包含在内；提交结果统一记录于当前台账。

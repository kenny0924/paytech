# 综合验证与本地验收记录

2026-09-14 阶段 5 证据。当前实施与下一确认点统一见[当前台账](../../../agent-docs/CURRENT_IMPLEMENTATION.md#platform-tenant-access-20260914)；阶段授权见 [delivery.md](./delivery.md)。

本次验证覆盖 3 个应用、8 项新业务 API（7 读 1 写）和 3 个页面，未新增表、字段或索引。42 项真实入口检查和 19 项浏览器检查通过，没有新增应用代码修复。详见[验证清单](./assets/stage5/verification.json)。

## 环境与证据边界

- 生产 Web 构建 → 本机入口代理 → 真实 Gateway → 真实 BFF → 真实 Platform → `127.0.0.1:13306/mor_platform_config_validation` 与本机 Redis。代理只转发，不返回模拟业务数据。
- 真实登录、JWT、动态路由快照、PLT-I02 和 PLT-I03 均参与；开发 Vault/签名适配器及本机服务发现沿用现有测试配置。未建设的 CUSTOMER 登录/会话链、CCT 投影、外部 Client 业务执行与压力测试未纳入本阶段。
- 使用本次独占的 4 个 Operations 账号、13 个 CLIENT/PARTNER Tenant、6 个 CUSTOMER 账号及其成员/授权/凭证。用户已批准临时启用 8 Route、3 Resource；原管理员 3 条新增权限关联保持 REVOKED。
- 67 个应用源文件与阶段 4 摘要一致，沿用 Platform 512/512、BFF 1163/1163、Web 938/938 和生产构建证据。Platform 指令 97.82%、分支 90.06%，既有门槛未变；本阶段不重复全量回归。

## 功能与交互验收映射

| 验收 | 实际证据与结果 |
|---|---|
| AC-01 | 13 个租户分页 10+3、CLIENT/PARTNER 筛选、含中文与 `_/%` 的名称筛选；INTERNAL 详情拒绝。浏览器列表与筛选结果一致。 |
| AC-02 | 来源已停用的租户恢复安全状态后仍 `tenantAccessAllowed=false`，页面继续显示来源限制。 |
| AC-03～04 | 真实暂停/恢复、只读命令 403；页面原因必填。只更新目标安全状态，其他字段及归属不变量复用阶段 4 真实 HTTP 检查。 |
| AC-05 | 真实 PLT-I03 认证依次 200 → 暂停后 401 → 恢复后 200。其他凭证/来源限制沿用阶段 4 覆盖。 |
| AC-06 | 旧暂停命令重放返回原结果而不覆盖恢复状态；旧版本 409、同状态不产生新成功审计。浏览器真实并发冲突保留原因并禁止原版本继续提交；审计页显示真实操作原因。 |
| AC-07～08 | 跨租户成员路径 404；四种角色授予和十三项权限分页；同权限四个来源仅一个有效，未来/过期/撤销及目录停用分别说明。损坏角色归属不泄漏关联信息，恢复后可重试。 |
| AC-09 | 成员与授权页面只读；仅租户查看账号不能查询成员，也无暂停按钮。API 拒绝直接越权，响应不包含凭证原文或邮箱 Token。 |
| AC-10 | 本机真实登录、菜单、权限和既有审计使用通过；其他旧能力沿用阶段 4 全量回归，未宣称 Risk 或 CUSTOMER 新运行链已验收。 |
| UI-01～03 | 列表→详情→成员、紧凑操作菜单、红色暂停、状态分列、角色/权限只读、长编码省略、权限来源展开通过。 |
| UI-04 | 无成员、无角色、无权限来源、无查看权限、受控服务错误分别展示；503 有 Request ID 和重试入口，不表现为空列表。 |
| UI-05 | 浏览器逐层返回保留筛选；真实 409 留住输入原因，刷新后可恢复；页面无重复返回按钮。 |
| UI-06 | 沿用阶段 3 已确认 Pencil 方案；1280px 与 390px 页面无整体横向溢出，宽表局部滚动。 |

接口检查详情见 [integration.json](./assets/stage5/integration.json)，页面检查见 [browser-check.json](./assets/stage5/browser-check.json)。视觉证据：[列表菜单](./assets/stage5/list-menu.png)、[权限来源](./assets/stage5/permission-sources.png)、[冲突保留原因](./assets/stage5/conflict-draft.png)、[服务失败](./assets/stage5/service-failure.png)、[空角色](./assets/stage5/empty-roles.png)、[无成员权限](./assets/stage5/no-member-permission.png)、[窄屏](./assets/stage5/member-mobile.png)。

## 执行与修正记录

使用 [harness/README.md](./assets/stage5/harness/README.md) 中的启动顺序和 `verify.py`。第一次手工脚本误按 Owner 的 500 检查公开入口；实际 BFF 依据现有依赖错误合同映射为 503/DEPENDENCY_UNAVAILABLE，符合 [API 错误定义](./api.md)。修正脚本断言并以本轮操作前审计数作为增量基线后，完整 42 项检查通过；应用代码未修改。

浏览器通过真实 API 制造另一操作者先提交的冲突；仅在本次独占角色上临时改变/恢复归属，验证失败关闭与重试，不修改其他数据。

## 恢复与停点

Web、Gateway、BFF、Platform 均正常停止，本次浏览器已退出关闭。Platform 清理自有夹具后，人工单连接执行 restore.sql 并核对 8/3 更新后 COMMIT。11 行恢复 DISABLED/version=1，原管理员 3 条关联仍 REVOKED；本期 105 行登记及目录计数保持不变。

CLIENT/PARTNER 租户、账号、会话、成员授权、凭证、命令、审计和签名测试记录均无残留。SQL 回执见 [local-activation.json](./assets/stage5/local-activation.json)，清理与端口核对见 [local-postcheck.json](./assets/stage5/local-postcheck.json)。

本阶段没有未关闭的验收缺陷。下一确认点为阶段 6 test 发布准备：只读核对目标环境，整理三个应用制品、105 行登记及启用 SQL、部署顺序与恢复方式；此处不构成 test 写入、发布、部署或提交授权。

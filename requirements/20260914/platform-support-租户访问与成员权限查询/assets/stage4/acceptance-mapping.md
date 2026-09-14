# 阶段 4 开发检查映射

本文件把需求验收项对应到代码及开发检查，不代替阶段 5 的三应用真实联调。当前结果及日志摘要见 [verification.json](./verification.json)，阶段状态以 [根台账](../../../../../agent-docs/CURRENT_IMPLEMENTATION.md#platform-tenant-access-20260914) 为准。

## 功能映射

| 验收 | 开发检查入口与检查内容 |
|---|---|
| AC-01/02 | Platform `TenantAccessManagementHttpAutoTest`：租户类型、中文/转义筛选、分页、INTERNAL 排除；安全恢复后仍保留来源限制。Web 解码用例检查双状态及访问结论一致性。 |
| AC-03/04 | Platform 当前授权、原因校验、版本上限、单目标资源锁、PLT-I03 用例；检查只改变目标安全状态和版本，来源投影及其他租户保持不变。BFF 八入口均检查当前授权拒绝先于 Owner 调用。 |
| AC-05 | Platform 真实管理 HTTP 暂停/恢复后调用真实 PLT-I03；其他租户不受影响，来源非 ACTIVE 时安全恢复仍拒绝。既有凭证回归覆盖凭证自身失效。 |
| AC-06 | Platform 旧命令重放、同状态不增版本、不重复审计、旧版本/同键异载荷冲突；短 Redis 租约到期后数据库行锁/CAS 保护；损坏回执不能覆盖当前状态。通过既有审计 HTTP 查询安全操作。 |
| AC-07 | Platform 同账号 CLIENT/PARTNER 两成员、路径串读 404；BFF/Web 检查返回的 Tenant/Member 必须匹配请求。 |
| AC-08 | Platform 未来/过期/撤销/停用、账号/成员/租户限制、跨租户/缺失/非法关联；多来源有效性、权限去重分页、计数同一 RR 快照。小规模执行计划见 [query-plan.json](./query-plan.json)。 |
| AC-09 | 本期仅一个安全状态写端点，无成员授权写接口。Owner 只查询安全显示列；BFF/Web 解码去除未公开字段，成员检查不返回明文邮箱及 Vault Token。 |
| AC-10 | 三应用既有套件检查兼容性。当前开发证据是 Platform 真实 HTTP/MySQL/Redis、BFF HTTP+Owner Simulator 和 Web 单元/合成页面；完整浏览器→BFF→Platform 链留待阶段 5。CUSTOMER 登录不在本期实现范围。 |

## 页面映射与证据边界

| 验收 | 开发检查及下一阶段关注点 |
|---|---|
| UI-01/02 | 合成浏览器检查列表→详情→成员权限、双状态、紧凑操作菜单、暂停原因弹窗与成功后状态更新。真实链恢复后仍受限的提示在阶段 5 核对。 |
| UI-03 | 真实页面组件显示只读角色/权限；长权限编码省略、来源按需展开。[成员页面截图](./member-browser.png)。 |
| UI-04 | 合成服务失败显示失败刷新、Request ID 与重试，[失败截图](./error-browser.png)。各权限和空数据状态的完整浏览器矩阵留待阶段 5。 |
| UI-05 | 浏览器返回恢复列表筛选及 URL；单元检查页签/分页/来源参数保留、同一载荷重试复用命令。写失败后的草稿保留及完整真实回退链留待阶段 5。 |
| UI-06 | 已确认的 Pencil 八画板及导出文件见 [阶段 3 页面方案](../../ui.md)，本阶段没有重画或覆盖该设计。 |

浏览器使用生产 React 组件与 BFF 合同合成数据，只修改内存场景；没有绕过正式应用认证、调用共享环境或创建持久样例。临时 Vite、预览文件及浏览器页已清理，详见 [browser-check.json](./browser-check.json)。

## 同合同内的修正

- 审计查询纳入精确的 `CHANGE_TENANT_SECURITY_STATUS + ACCESS_TENANT` 组合，修复已记录操作被原 Operations action 前缀筛选排除的问题。
- 查询来源解释使用数据库 UTC `evaluatedAt`；安全命令与既有审计/幂等使用应用时钟，开发测试可固定业务时刻并清理自身审计。
- HTTP 测试对已编码查询使用绝对 URI，避免测试客户端二次转义中文和脱敏邮箱条件。
- 租户/成员字段完整性复用正式 Schema 的 NOT NULL 与严格枚举映射；Service 保留正 ID/版本、CUSTOMER/HUMAN、所属租户和关联完整性检查，不重复实现数据库约束。
- 对新增目录/页面/Processor 同步既有清单预期，保留完整目录、架构、跨模块及并发门禁；未下调覆盖率要求。

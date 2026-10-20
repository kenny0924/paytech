# I2 线遗留与上游缺口（2026-10-20）

> I2 不改 commercial 服务代码；以下是实现中发现的 H 线接口缺口、跨系统遗留项与本线的设计取舍，供主会话决定后续归属。

## commercial（H 线）接口缺口

| # | 缺口 | 影响 | 建议 |
|---|---|---|---|
| G1 | 版本驳回 `POST /commercial-terms/v1/versions/{id}/reject` 的 `CommercialTermVersionCommandRequestBody` 只有 `expectedChangeVersion`，不接收驳回原因 | 任务书 F2「驳回需填原因」无法落地；工作台驳回弹窗只确认、不收集原因，页面注明"commercial 驳回接口目前不接收原因" | commercial 在驳回命令增加 `reasonText`（写入版本 BCR 快照 `_meta` 或版本字段），BFF / Web 再接入 |
| G2 | `CommercialTermVersionSummary` 只有 `agreementId`，没有合同号 | 工作台审批队列、版本列表的「合同」列显示合同 ID（可点击进入详情），不是设计稿中的合同号 | 版本 / 方案列表行补 `agreementNo`（同库 JOIN 合同主表） |
| G3 | `BusinessChangeQueryApi` 只能按聚合根分页，不能按 recordType + recordId 或"某版本之前"读取 | BFF 为求上一快照需向后回溯：本页之后按 200 条一页至多 5 页（1000 条），超出标 `NOT_COMPARED`；聚合根快照极多时较早条目不比较 | commercial 在快照行带 `previousVersionNo`，或提供按 recordType + recordId + versionNo 上界的读取 |
| G4 | commercial-api 未发布合同状态、版本状态的 `CodeEnum`（只在 core 模块内） | BFF 新增消费方枚举 `CommercialAgreementStatus`、`CommercialTermVersionStatus` 用于工作台计数；Controller 校验仍用字面正则 | 如 H 线后续发布到 `api/enums`，BFF 改为引用并删除本地枚举 |

## 跨系统遗留（client-master 需求）

- **Client 选择器**：本期按任务书实现为「ID 输入 + 本机最近使用（localStorage，最多 10 个）+ 审核通过的 Client（compliance 审核任务 `status=APPROVED` 第一页 50 条，只给 clientId / caseNo）」，用于合同列表筛选、新建合同、费率预览。审核任务列表需要入驻审核只读权限；无权限或失败时只显示最近使用。
- **未做**：按 Client 名称 / 编号搜索、列表与选择器显示 Client 名称、Party 与 OE 下拉（Party ID、OE entityNo 仍手输）。需要 client-master 提供运营可调用的 Client 摘要 / 搜索接口与 OE 列表接口（mor-test 目前无 OE 数据）。

## 本线取舍（与任务书的差异）

- **BFF 路径**：任务书写作 `/commercial-admin/v1/commercial-term-plans`、`/commercial-term-versions`；实现沿用现有命名空间 `/commercial-admin/v1/commercial-terms/plans`、`/commercial-terms/versions`、`/commercial-terms/versions/{baseVersionId}/diff/{targetVersionId}`（与既有创建方案、版本详情、价格路由一致，也与 technique.md §2 的对比路径一致）。Platform 路由与 Web 同步使用该路径。
- **工作台**：任务书写「四个计数卡」但列出五个计数；实现返回并展示五张卡片（新增 `DRAFT_AGREEMENTS`）。I1 的 `PENDING_INTEGRATION` 状态与 `CommercialDashboardMetricStatus` 已删除（数据接入后不再有"待接入"）。工作台路由权限保持 I1 的 `COMMERCIAL_CONTRACT_READ`，审批队列中的版本数据随之对合同只读可见；批准 / 驳回仍要求 `COMMERCIAL_PRICING_APPROVE`。「最近变更」卡片按任务书不做（没有全局 BCR 流）。
- **目录权限**：产品目录三个路由与目录菜单一致绑定 `COMMERCIAL_CONTRACT_READ`。价格编辑抽屉的计费事件下拉也读该接口；只有定价权限、没有合同只读的角色读取失败时，计费事件改为手输（可开通项、订阅产品同理），保存仍由 commercial 校验。
- **历史比较的取值语义**：`changedFields` 按快照 `data` 键的并集逐值比较（JSON 结构相等）；首版快照把全部字段视为变化。

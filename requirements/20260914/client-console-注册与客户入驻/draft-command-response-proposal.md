# 草稿写接口固定回执决定

状态：2026-09-15 用户回复“可以”，确认固定操作回执方案。本文收口 C04/C06 响应与幂等重放语义，[API 合同](./api-contract.md)同步引用；不恢复资料版本。

原合同问题：C04/C06 曾要求同一幂等键重放原操作结果，同时写接口返回完整 Application。资料后来继续修改后，原始完整返回值不能仅从当前资料重建；保存另一份原表单会违反已批准的“不保留资料历史”。现有命令记录的 response_ref 只能保存短引用，也不能把资料或 Vault 值塞进通用审计、命令记录或补偿 Payload。

已确认 C04/C06 改为固定操作回执，成功后由 C05 查询最新资料：

```json
{
  "commandId": "原 Idempotency-Key UUID",
  "applicationId": "稳定申请 ID",
  "applicationNo": "稳定申请编号",
  "clientId": "稳定 Client ID",
  "clientType": "INDIVIDUAL",
  "operation": "CREATED",
  "completedAt": "2026-09-15T00:00:00.000000Z"
}
```

operation 精确为 CREATED/RESTORED/SAVED。首次创建仍为 201，恢复或保存为 200；同键同参返回原回执和原状态，异参冲突，旧申请结束后重放不能创建新申请。completedAt 表示原操作完成时间，不能当作当前表单的最新保存时间；当前状态、字段和 allowedActions 始终以随后 C05 为准。

持久化可使用现有 response_ref 的 `操作:申请ID` 短引用及首次完成时间；申请/Client/类型等不可变字段从原记录读取，不保存历史表单，不增表、增版本或执行迁移。重放不能刷新首次完成时间。验收必须覆盖同键重放、资料后续改变、终态后重放和旧页面恢复，结果见当前实施台账。

评审时另一可行选择是写接口返回当前最新资料，并明确重试也返回最新资料；这样保留写入幂等，但取消“原完整响应逐次相同”的承诺。两种方案都不会恢复资料版本，差别是响应契约及前端是否随后查询 C05。


## 后续 C07—C11 人员与关系命令

2026-09-15 按用户继续阶段 4 的指示，将已确认的固定回执原则应用到人员与关系命令。C07/C08 原先的 Person 和 C09/C10 原先的 RelationResult 同样不能在不存历史的条件下重建原字段值，因此成功写入统一返回 PeopleCommandReceipt；当前属性仍由 C05 获取。此项是既有“不保存资料版本、重放原结果”原则的接口收口，不新增资料副本。

固定字段为 commandId/applicationId/partyId/relationId/operation/completedAt。C07 为 PERSON_CREATED/201，C08 为 PERSON_SAVED/200（relationId=null），C09 为 RELATION_CREATED/201，C10 为 RELATION_SAVED/200；C11 保持无 Body、204。同一命令的 HTTP 状态和回执固定，后续编辑、移除关系、重建同类关系或进入终态均不能改写原回执或重做业务动作。

命令记录使用 PC/PS/RC/RS/RD:<partyId>:<relationId> 短引用，PS 的关系坐标用 0 表示不适用；两个 19 位 ID 加前缀/分隔符最多 42 字符，小于现有 response_ref 上限。命令摘要包含申请、路径目标、账号、操作与规范化字段；删除后的原回执只依赖此固定引用，不能再查询已经移除的关系来重建。新的删除键访问已删除关系返回 404；原删除键重放仍为 204，不能误删后来创建的新关系。

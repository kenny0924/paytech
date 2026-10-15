# 退回后修改证件：实施方案（评审稿）

> 状态：2026-10-15 用户回复“先简单的改，让流程能跑通，后面我会规范化一下这个”，按简化版实施：只要改证件字段即解绑，旧键保留；只补主主体退回重提一条测试，第 4 节其余场景未补。实施记录见[实现台账](../../../agent-docs/CURRENT_IMPLEMENTATION.md#client-console-onboarding-20260914)。
> 原状态：2026-10-15 评审稿。本稿不新增业务规则，只把已批准规则落到代码；不含 DDL、SQL 执行、部署或提交。
> 依据：[C14 修订稿](./submission-contract-amendment.md)第 76 行“RETURNED 后修改证件再提交，绑定新键；旧键保留原归属，不自动删除”；[数据设计](./data-design.md)第 44 行“已绑定永久键的组成字段不可原位改写……更正时重新核验并绑定，旧永久身份键仍保持原 Party 归属”。

## 1. 问题

首次提交（C14）时，CCT 为每个受审主体把证件行 `t_party_profile_identifier.identity_key_id` 绑定到永久身份键 `t_party_identity_key`。运营退回后申请回到 RETURNED，其他字段都能改，但证件改不了：

| 位置 | 现状 |
|---|---|
| 主主体保存 C04：`ClientDraftRepositoryImpl.save` | 证件行已绑定（`boundIdentifiers != 0`）就抛 `APPLICATION_NOT_EDITABLE`（409） |
| 关联人员保存 C08：`ClientPeopleRepositoryImpl.savePerson` | 同样条件抛 `PERSON_NOT_EDITABLE`（409） |
| `ClientDraftMapper.saveIdentifier` | `WHERE ... AND identity_key_id IS NULL`，已绑定行更新 0 行 |

结果：退回原因多半就是证件填错，客户却只能看到“当前申请资料不可修改”，既改不了也没法正确重提，申请卡死。已批准规则要求的“解绑旧键 → 重提时绑定新键”路径没有实现。

## 2. 方案

### 2.1 保存时解绑（C04 / C08）

满足以下全部条件时，同一本地事务内先解绑、再保存证件字段：

1. 本次修改包含任一证件字段（类型、签发国、号码、有效期起止）；
2. 申请为 RETURNED（DRAFT 状态不会有已绑定行，SUBMITTED/UNDER_REVIEW/终态仍不可编辑，沿用现有判断）；
3. 主体可编辑：主主体用 `ClientDraftPolicy.isEditable`；关联人员用 `isPersonEditable`（已批准、被其他申请或协议共享的人员仍返回 `PERSON_NOT_EDITABLE`，不解绑）。

解绑是条件更新：`SET identity_key_id = NULL WHERE profile_identifier_id = ? AND identity_key_id = <读取到的原键>`，影响行数不为 1 则回滚。之后走原有 `saveIdentifier`（其 `identity_key_id IS NULL` 条件此时成立）。

**只要改证件字段就解绑，不判断“号码是否真的变了”。** 理由：重提时如果规范化后仍是同一号码，现有 `bindIdentity` 会找到同一 Party 的原键直接复用，结果与不解绑相同；而“是否变化”的判断要在保存时重复做规范化，增加一条容易出错的分支。

### 2.2 重提时绑定（C14，不改代码）

现有逻辑已覆盖全部情况：

| 重提时证件规范化结果 | 行为 |
|---|---|
| 与旧号码相同 | 找到同一 Party 的原键，复用 |
| 新号码，库中不存在 | 新建身份键并绑定 |
| 新号码，已属于其他 Party | 提交返回 `IDENTITY_CONFLICT`，C16 核对同样提示 |
| 改回更早用过的号码 | 找到同一 Party 的旧键，复用 |

Risk 侧不需要改：每次重提生成新 submissionId 和新审核任务，I01 携带当时的证件资料。

### 2.3 旧键

按已批准规则保留：旧 `t_party_identity_key` 行仍归原 Party，不删除。

**已知后果：** 如果客户误填的是别人的真实号码，这个号码会一直被本 Party 占用；真正的主人以后入驻会在提交时收到 `IDENTITY_CONFLICT`。已批准规则把清退误填标识定为“人工身份治理”，本次不实现，只在台账中列为已知限制。

### 2.4 并发

C04、C08、C14 都使用同一把 `client-onboarding:application:{id}` 锁，保存与提交不会交错。解绑的条件更新和 `bind` 的条件更新是数据库层的兜底保护。

## 3. 改动范围

| 层 | 文件 | 改动 |
|---|---|---|
| Mapper | `PartyProfileIdentifierMapper`（接口 + XML） | 新增 `unbind`：按行 ID 和原键条件置空 |
| Repository | `ClientDraftRepositoryImpl.save`、`ClientPeopleRepositoryImpl.savePerson` | 已绑定且满足 2.1 条件时先解绑；其他情况保持原有 409 |
| 测试 | CCT `auto/ext/http/onboarding` 下现有提交/草稿测试 | 见第 4 节 |

不改：API 合同、错误码、HTTP 状态（原来 409 的场景在 RETURNED 下改为成功）、BFF、Risk、DDL。Web 实现时核对一遍：如果证件字段在已提交过的申请中被设为只读，一并放开；否则 Web 不改。

## 4. 测试（从 C04/C08/C14 HTTP 入口触发）

1. 主主体：提交 → 退回 → 改号码 → 重提成功；证件行指向新键，旧键仍在且归本 Party。
2. 主主体：退回后改回同一号码（只改格式，如大小写、空格）→ 重提复用原键，不新建。
3. 主主体：退回后改成其他 Party 已占用的号码 → C16 提示冲突，C14 返回 `IDENTITY_CONFLICT`，申请保持 RETURNED。
4. 关联人员：同 1，C08 保存。
5. 关联人员已被其他申请共享或已批准：退回后改证件仍返回 `PERSON_NOT_EDITABLE`，绑定不变。
6. SUBMITTED / UNDER_REVIEW / APPROVED 下改证件仍被拒绝，绑定不变。
7. 退回后只改有效期：解绑后重提复用原键。

## 5. 不做

- 旧键清退、身份治理工具。
- 被拒绝后的终止语义（另出设计）。
- 入驻写接口的请求幂等迁移（后续单独处理）。

## 6. 需要你确认

1. 第 2.1 节：只要改证件字段就解绑，重提时同号码自动复用原键。（建议）
2. 第 2.3 节：旧键按已批准规则保留，误填他人号码的清退留给人工身份治理。（建议）
3. 确认后进入实施：只改 CCT（及必要时 Web 只读处理），跑 CCT 全量 `verify`，不部署、不提交。

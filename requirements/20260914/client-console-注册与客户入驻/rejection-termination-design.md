# 最终拒绝后的收尾与重新申请：设计稿（评审稿）

> 状态：2026-10-15 用户回复“按照建议”，第 5 节四项按建议确认；实施中材料改为重新上传，见第 6 节。实现与验证只见[实现台账](../../../agent-docs/CURRENT_IMPLEMENTATION.md#client-console-onboarding-20260914)。
> 已批准的边界（不改）：REJECTED 是申请终态，不可编辑、不复活（[需求](./requirement.md) AC-13）；拒绝后“可按既有身份/资格规则发起下一份申请”（[设计](./design.md)第 48 行）；REJECTED 不直接映射为 Tenant TERMINATED，Client 生命周期由 CCT 决定（[PLT-I17 合同](./platform-tenant-projection-contract.md)第 47 行）。

## 1. 现状

| 对象 | 拒绝后的状态 | 来源 |
|---|---|---|
| CCT 申请 | REJECTED，释放账号名额，写决定 ID | `ClientReviewOutcomeServiceImpl.conclude` 只在通过时继续处理 |
| CCT Client | 一直 PENDING | 同上 |
| CCT 身份键 | 主体和关联人员的永久身份键仍归原 Party | C14 提交时绑定 |
| Platform 工作区 | PENDING Tenant + 申请人 OWNER 成员 + 入驻角色，保持不变 | PLT-I17 只收到过开通 |
| 客户页面 | 显示拒绝说明，并提示“此申请不能再修改；如需继续，请重新发起申请” | `client-console-web/src/onboarding.ts` 的 `resultBanner` |

## 2. 问题

**问题 1（阻塞重新申请）：同一证件再申请会被判为冲突。** 账号没有进行中的申请时，C01 开始申请（`ClientDraftServiceImpl.create`）总是新建 Party、Client、Profile 和申请。客户被拒后用同一本护照或同一个公司注册号重新申请时，旧 Party 仍占着这些身份键，新 Party 提交会返回 `IDENTITY_CONFLICT`，而且客户自己无法解决。这是读代码得出的结论（`ClientSubmissionSubject.identityOwnedByOther`），还没有用测试复现。关联人员同理。

**问题 2：被拒的 Client 和工作区一直挂着。** 每次重新申请都会多出一个 PENDING Client 和一个 PENDING 工作区。现在不影响上传，因为上传按 Client 定位工作区（`OnboardingUploadProcessor` → `lockUploadTenant(accountId, sessionId, clientId)`）。但以后做客户侧工作区列表时，这些残留会出现在列表里。

## 3. 方案：同类型重新申请复用原 Client

数据模型本来就支持一个 Client 下有多份申请：`uk_client_application_active(client_id, active_slot)` 只限制同时进行中的申请只能有一份；`uk_client_party` 和 `uk_party_profile_party` 让 Client、Party、Profile 一一对应。所以：

1. **C01 开始申请：** 账号没有进行中的申请，且最近一份申请为 REJECTED、类型相同、所属 Client 仍为 PENDING 时，在**原 Client** 下新建一份 DRAFT 申请（新申请编号、新名额），不再新建 Party 和 Client。
2. **带入资料：** 同一事务内，把主体 Profile 和该申请下关联人员 Profile 的 `draft_application_id` 指向新申请；复制旧申请的关系行和材料引用到新申请。客户看到的是上次提交的内容，改需要改的部分即可。旧申请行、Risk 旧任务与旧证据都保留不动。
3. **身份键：** 主体仍是同一个 Party，重提时现有逻辑会找到自己的身份键直接复用，问题 1 消失；改了证件则走[退回后改证件](./returned-identifier-rebinding-proposal.md)的同一套解绑与重绑逻辑。
4. **Client 与工作区：** Client 保持 PENDING，含义是“尚未通过审核的客户”。不向 Platform 发新的投影，继续使用原来的 PENDING 工作区，问题 2 在同类型场景下消失。
5. **Risk：** 不改。每次提交仍生成新 submissionId 和新任务；因为主体 Party 不变，运营在 Risk 里能看到同一主体的历次申请。
6. **换类型重新申请**（个人 ↔ 组织）：Client 类型由 Party 类型决定，无法复用，仍按现有逻辑新建 Client。旧 Client 保持 PENDING，旧工作区清理留到做工作区列表时一并处理。

**不采用的两种做法：**

| 做法 | 不采用的原因 |
|---|---|
| 拒绝时释放身份键，重新申请新建 Party | 违反“永久身份键”的已批准规则；CCT 失去同一主体历次申请的关联，运营看不出是同一人反复申请 |
| 拒绝时把 Client 终止，并把工作区投影为 TERMINATED | 不解决问题 1；终止的 Client 不能再申请，还要另建 Party，又回到身份冲突 |

## 4. 改动范围（确认后实施）

| 系统 | 改动 |
|---|---|
| CCT | C01 增加“复用最近被拒的同类型 Client”分支；新增带入资料的 Repository 方法（Profile 指针更新，关系与材料引用复制）；测试：拒绝 → 重新申请 → 资料带入 → 同证件重提成功 |
| Client BFF | 预计不改：C01 合同不变，响应仍是申请 ID |
| Client Web | 拒绝提示旁增加“重新申请”按钮，直接调用现有 C01 进入新申请；实施时核对开始页现有入口，能复用就不新增 |
| Platform、Risk | 不改 |
| 数据库 | 无 DDL；现有唯一索引已覆盖 |

## 5. 需要你确认

1. **重新申请时复用原 Client（第 3 节）。** 建议采用。
2. **被拒后是否可以立即重新申请。** 建议本期允许立即重新申请、不设冷静期，运营在 Risk 历史里能看到同一主体的多次申请。如果需要“永久拒绝、不得再申请”，要在运营拒绝时增加一个选项，另行设计。
3. **带入范围。** 建议资料、关联人员、关系、材料引用全部带入，客户自己删改。另一种做法是只带入资料，材料要求重新上传。
4. **换类型重新申请。** 建议新建 Client，旧 Client 保持 PENDING，残留工作区留到做工作区列表时清理。

## 6. 实施中的调整（2026-10-15）

1. **材料不带入，改为重新上传。** Platform 文件归属是申请编号加主体（`OnboardingFileReadProcessor.verify` 比对 applicationId），旧材料引用带到新申请后核验会判定文件不存在。用户选择“材料重新上传”，Platform 不改。
2. **关系是转移不是复制。** `uk_party_relation_identity(organization_party_id, related_party_id, relation_type)` 让同一关系只能有一行，所以把草稿关系的 `client_application_id` 改指新申请。被拒的旧申请在 CCT 里不再显示这些关系和资料（资料本来就是单份 Profile）；旧申请提交时的内容仍在 Risk 旧任务中。
3. **Web 不改。** 开始页在没有进行中申请时本来就显示“以个人/组织身份入驻”，客户选同一类型时 CCT 自动复用原 Client。


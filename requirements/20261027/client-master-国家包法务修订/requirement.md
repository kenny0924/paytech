# client-master：国家包法务修订（AE / SA / EG）

- 状态：阶段 1 已确认；阶段 2+3 已确认（2026-10-27，含 technique 第 2.2 节两处调整，已改入本文）；阶段 4 进行中。
- 来源：用户 2026-10-27 提供的法源研究 [legal-research-26-items-2026-10-27.md](../../../design-discuss/client-onboarding/legal-research-26-items-2026-10-27.md)，对应设计 [design.md 第 9.5a 节](../../../design-discuss/client-onboarding/design.md)。该文件不是当地律师签署的意见或上线批准。
- 前序：[全球合规入驻与资格切片 1](../client-master-全球合规入驻与资格切片1/requirement.md)（国家包已以 ACTIVE 在本地与 mor-test 验收，提交 `9f506f0f`）。
- 并行需求：[参考数据目录与 client-master 迁移](../platform-support-参考数据目录与client-master迁移/requirement.md)（已上 mor-test，删除了 `t_catalog_item` 中 48 行）、[参考数据客户端组件](../platform-support-参考数据客户端组件/requirement.md)（阶段 1，也改 client-master）。本需求的 DML 只做增量，不重跑切片 1 的 country-pack 脚本。

## 1. 目标

把法务 26 项中"现有能力可承接、只需修订国家包"的结论落到 AE / SA / EG 国家包，并补上声明证据的两个缺口（条款版本未记录、声明不绑定资料版本）。做完后，这些国家包行在法务意义上不再阻碍 ACTIVE 发布；第 9.5a 节其余需要业务决定或新能力的项单独跟踪。

## 2. 本期范围

| 编号 | 法务项 | 内容 |
|---|---|---|
| S-1 | 3 | AE：新增 ERN（统一经济登记编号）属性；签发机关目录增加"其他"，选"其他"时必须填写签发机关名称 |
| S-2 | 11 | SA：机构表单去掉"执照到期日"，改为 CR 年度确认日；CR 号保持 10 位数字，不加前缀白名单 |
| S-3 | 15 | SA：自由职业者使用专用表单，在本人证件之外必须提供 Freelance Work Document 号（`FL` 前缀，长度不限定）与有效期（阶段 4 调整：以属性采集，见 technique 3.1 注） |
| S-4 | 19 | EG：商业登记拆为"统一 15 位商业登记号"与"本地 CR 号"两个标识类型（每个主体只有一个证件位，二选一，类型分开保存）；新增增值税登记号属性，与 9 位税卡号分开 |
| S-5 | 23—25 | 声明拆分：原 `PLATFORM_TERMS` 拆为"接受平台使用条款""确认收到隐私告知"；机构申请另加"确认有权提交关系人资料并会向其提供隐私告知" |
| S-6 | 23、25 | 声明证据补全：记录实际展示的条款 / 告知版本；每条声明绑定其针对的申请资料版本，声明后资料再有实质改动，原声明失效，提交前须重新确认 |
| S-7 | 13 | 受益人门槛比较方式改为可配置（`GTE` 25% 或以上 / `GT` 超过 25%），默认 `GTE`；AE / SA / EG 显式配置 `GTE` |
| S-8 | 18 及文档 | 文档：第 9.5a 节逐项标注结论与状态；修正过时引用（SA 第 99 号决定取代第 235 号 / MD 267；EG 15 位统一号已实施）；EG 25% 标为"候选规则，待银行 / PSP 书面确认" |

采集项变化通过**新的 Schema 版本**发布：已有申请继续使用其冻结的旧版本，不受影响；新申请使用新版本。

## 3. 不做项

- 需要业务决定或新能力的法务项：第 10 项沙特代操作模式、第 12 项 Wathq、第 6 项 ICP 网关、第 8 / 16 / 20 / 21 项数据存放与跨境、第 7 项外国 PEP 高管批准、第 24 项向关系人发送隐私告知的机制本身、第 17 / 22 / 26 项留存与直签监控。另行跟踪，见第 7 节。
- 第 1、2、4、13（门槛数值）、14 项：现状已符合，不改。
- 登记状态（如 SA CR 状态、AE 许可证状态）由申请人自填没有核验价值，等 S6 登记库核验时由核验结果写入，本期不加自填字段。
- 不改变国家包的 ACTIVE / DRAFT 发布口径；生产发布仍须法务对其余项确认。

## 4. 逐系统变动点

| 系统 | 变动点 | 类型 |
|---|---|---|
| client-master | `t_identifier_type` +2（`EG_UNIFIED_CR`、`EG_LOCAL_CR`），原 `EG_COMMERCIAL_REGISTER` 保留给旧版本 Schema | 增量 DML |
| client-master | `t_attribute_definition` +8（机构 `AE_ERN`、`AE_REGISTRATION_AUTHORITY_NAME`；个人独资 `AE_LICENSE_ERN`、`AE_LICENSE_AUTHORITY_NAME`；`SA_CR_ANNUAL_CONFIRMATION_DATE`、`SA_FREELANCE_DOCUMENT_NUMBER`、`SA_FREELANCE_DOCUMENT_EXPIRY_DATE`、`EG_VAT_REGISTRATION_NUMBER`）；属性按主体类型区分，同一代码不能跨机构与个人 | 增量 DML |
| client-master | `t_catalog_item` +1（`REGISTRATION_AUTHORITY.AE_OTHER`） | 增量 DML |
| client-master | `t_rule_setting`：新规则 `UBO_THRESHOLD_COMPARISON`（AE / SA / EG = `GTE`）；新标识类型的 `ACQUIRE_MODE` / `VAULT_REQUIRED` / `EXPIRY_REQUIRED` | 增量 DML |
| client-master | `t_onboarding_schema` / `_field`：受影响 Schema 发布 v2（AE 机构 2、AE 个人 2、SA 机构 1、SA 个人 1 + 新增 SA 自由职业者 1、EG 机构 1、EG 个人 1，以阶段 2 核对为准），旧 v1 退出 active slot | 增量 DML |
| client-master | `t_application_declaration` 新增"声明针对的资料版本"列 | DDL |
| client-master | 申请草稿需要可比较的资料版本（版本号或摘要）；声明写入时记录，提交时校验所有声明仍与当前版本一致 | 代码 |
| client-master | `RuleCode` 登记 `UBO_THRESHOLD_COMPARISON`，`RelationRules` 按配置比较；表单 Schema 接口回传比较方式 | 代码 |
| client-master | 测试：新 Schema v2 采集、FL 号码规范化与校验、EG 两种登记号、声明失效与重新确认、比较方式 `GT` / `GTE`、v1 申请不受影响 | 测试 |
| compliance | 不改：审核登记虽接收声明但无任何消费者；运营查看声明读 client-master（technique 2.2） | 无 |
| client-console-bff | 声明接口透传条款版本与资料版本、"已失效"状态 | 代码 |
| client-console-web | 声明步骤：三项分开勾选，条款与隐私告知可打开阅读，提交时带上所展示的版本；资料改动后声明显示"需重新确认"；SA / EG / AE 新字段由 Schema 驱动渲染（核对 `FL` 号、"其他"签发机关的条件显示） | 代码 + 文案 |
| operations-console-bff / web | 案件详情新增声明区块（声明、取值、时间、账号、条款版本、是否针对提交版本）；新 Schema 字段由现有 Schema 驱动渲染 | 代码 + 文案 |
| Platform | 语言包：client-console、operations-console 新文案（en / zh-CN）发布新版本；无新增路由（阶段 2 核对） | 语言包 DML |
| 文档 | 设计第 9.5a 节、PDManer 模型（client-master、compliance）、`client-master-initial-data.sql` 同步、CURRENT_IMPLEMENTATION | 文档 |

## 5. 验收标准

功能：

- **AC-01** AE 机构新申请可填 ERN（可选）；签发机关选"其他"时必须填写机关名称，否则提交报字段错误。
- **AC-02** SA 机构新申请不再出现"执照到期日"，可填 CR 年度确认日；CR 号以 7 开头或旧号均可通过格式校验（10 位数字）。
- **AC-03** SA 自由职业者新申请在本人证件之外必须提供 Freelance 文件号与到期日；`FL-ABC123`、`fl 123` 通过格式校验（按原样保存），`AB123` 报格式错误。
- **AC-04** EG 机构新申请在 15 位统一商业登记号和本地 CR 号中选一种填写（至少一项），所选类型随号码保存；增值税登记号单独填写；9 位税卡号不再被当作增值税号展示。
- **AC-05** 新申请的声明为三项（机构）或两项（个人），旧的单一 `PLATFORM_TERMS` 不再出现在 v2 Schema。
- **AC-06** 声明记录包含实际展示的条款 / 告知版本；版本为空时拒绝写入（v2 Schema）。
- **AC-07** 声明后修改任一实质资料（主体、证件、关系人、地址、业务、文件），原声明显示失效；未重新确认即提交返回明确的字段错误；重新确认后可提交。
- **AC-08** 运营案件详情展示声明区块：每项声明的取值、时间、账号、条款版本，以及是否针对提交的资料版本。
- **AC-09** `UBO_THRESHOLD_COMPARISON=GT` 时 25% 持股不算受益人，`GTE` 时算；未配置时按 `GTE`。
- **AC-10** 已有 v1 申请（包括 mor-test 上进行中的申请）继续按 v1 表单编辑和提交；v1 的 `PLATFORM_TERMS` 不要求条款版本。声明失效规则对所有申请生效：存量声明（资料版本为空，按 0）在部署后到下一次改资料前仍有效。
- **AC-11** 设计第 9.5a 节 26 项均有结论与状态（已符合 / 本需求修订 / 待决定 / 待法务确认）。

视觉 / 交互：

- **AC-V1** 声明步骤三项分开、各自说明清楚；条款和隐私告知可在页面内打开阅读；失效声明有明显提示，并可逐项重新确认（不提供一键全部确认，以保留逐条声明证据；阶段 5 调整）。en / zh-CN、1440 / 390 两种宽度下布局正常，无未翻译的键。
- **AC-V2** 运营详情中新字段和声明版本信息排版与现有详情一致，布尔值不显示为 true / false。

## 6. 关键决定与待决问题

已定（来自法务文件与本次讨论）：门槛数值保持 25%；号码不加未经确认的前缀或校验位；登记状态不由申请人自填。

用户 2026-10-27 确认（"按建议就绪"）：

- **Q1** EG 新申请统一登记号与本地 CR 号**至少填一项**。
- **Q2** 声明后**任何草稿写入**都使原声明失效。
- **Q3** 本期使用占位版本的平台使用条款与隐私告知（如 `2026-10-draft`），页面标注"草案"；正式文本另行替换。
- **Q4** 门槛比较方式可配置纳入本期。

## 7. 本期之外仍需跟踪的法务项

| 项 | 内容 | 去向 |
|---|---|---|
| 10 | 沙特：平台运营不能实质代 PayFac 做 KYC；SA 在 S-P（Partner 门户工作台）上线前不宜 ACTIVE | 需用户决定，影响 AGENTS.md Partner 例外条款 |
| 12 | 沙特 Wathq UBO 核验与差异上报（2026-03-09 起） | S6 |
| 6 | 阿联酋 ICP 网关核验 | S6 |
| 8、16、20、21 | 三国数据存放、境外云审批、埃及跨境许可与本人同意 | 部署架构决定 |
| 7 | 外国 PEP 由承担义务机构高管批准 | 合规程序（新角色） |
| 24 | 向关系人提供隐私告知的机制 | 后续切片 |
| 17、22、26 | 留存起算点、卡组织直签门槛监控 | 数据合规项目、S7 |

## 8. 适用阶段（建议）

阶段 1—8 均适用。本需求以 DML 为主，阶段 2 与阶段 3 可合并为一次评审；mor-test 部署替换 client-master、compliance、两个 BFF、两个 Web 并发布语言包，需与参考数据客户端组件需求错开。

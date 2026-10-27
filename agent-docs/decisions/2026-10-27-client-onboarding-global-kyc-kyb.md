# 客户入驻与资格域（client-master + compliance）：全球合规入驻、KYC/KYB 与 PayFac 合规服务决定

- Decision status: Proposed（2026-10-26—27 用户在讨论中逐条确认第 2 节 ①—⑦ 与 Q1—Q17；作为仓库决定仍待用户确认登记）
- Implementation status: Not started（切片 1 需求已登记：[requirements/20261027/client-master-全球合规入驻与资格切片1](../../requirements/20261027/client-master-全球合规入驻与资格切片1/requirement.md)）
- Deployment status: Not deployed
- Decision date: 2026-10-27
- 详细设计：[design-discuss/client-onboarding/design.md](../../design-discuss/client-onboarding/design.md)（19 节 + 四国扩展演练 + 硬编码边界）、`client-onboarding.pen`（12 张总板 + SM-A…SM-Q 十七张状态机图）、`assets/research-*.md`（行业 / 三国监管 / 现有实现三份调研）。
- 实现、验证与部署状态只见[当前台账](../CURRENT_IMPLEMENTATION.md)。

## 1. 决定

| # | 决定 |
|---|---|
| ① | **compliance 不并入 client-master**；两服务按一个"客户入驻与资格域"设计：client-master 回答"你是谁、你填了什么"（身份、资料、关系人、申请、采集规则），compliance 回答"你够不够格、还够不够格"（程序、案件、核验、CRR、决定、持续复审、结算控制）。只经 HTTP 合同与 MQ 事件协作，不共享库。 |
| ② | **平台只提供系统，入驻审核由 PayFac 决定**（Q1）：案件记责任方 `responsible_partner_id` 与操作方 `operated_by`；决定人身份可为 PayFac 账号；平台运营只对 AFFILIATED PayFac 代操作。按用量计费给责任 PayFac（KYB/KYC 案件、筛查、检查）。AGENTS "Partner 只读"规则已于 2026-10-27 加合规例外。 |
| ③ | 首批辖区 **AE、SA、EG**；国家差异全部落在数据：辖区包 `t_jurisdiction`、规则开关 `t_rule_setting`（键值）、组织类型 / 标识类型 / 关系类型 / 地址格式组件 / 扩展属性目录、采集 Schema（版本化、同时是前端渲染模板）、compliance 程序（辖区 × 主体类型 × 角色 × 档次 × Owner）。代码中禁止按辖区 / 类型分支；只有校验位算法与供应商渠道脚本两个插件位；CI 以虚构国家 `ZZ` 的纯 DML 跑通全链作为门禁。 |
| ④ | 首期**人工审核 + 筛查 Mock 渠道 + 人工筛查兜底**；IDV / 登记库 / 税号核验作为 channel-connector SERVICE 标准 API 预留（`SCREENING_*`、`GOV_ID_VERIFY`、`KYB_REGISTRY_LOOKUP`、`TAX_ID_VERIFY`、`ADDRESS_VERIFY`、`IDV_*`）；生产上线以真实筛查供应商接入为门禁。 |
| ⑤ | **个人证件号进 Vault**（新数据类型 `LEGAL_IDENTIFIER_V1`，STABLE Token 兼作 `t_party_identity_key` 唯一键）；机构登记号 / 税号明文；姓名、生日明文 + 访问控制。 |
| ⑥ | **Client 填 PayFac 发的入驻码**（`t_partner_onboarding_code`）确定责任方，不暴露 Partner 列表（Q4）；无码 = 平台自营。 |
| ⑦ | **CRR（客户风险评级）本期必做**：S1 手工必填、S4 自动评分；没有 CRR 的案件不能批准。 |
| Q2–Q17 | 四眼 = compliance 推荐人 + 决定人（程序可关）；PEP 自述 + 筛查双轨；重提同案件多轮；C5 子商户延后；供应商回调首期拉取；AE/SA 不一定本地化；USD 1M 由 billing 告警；修订重大性归 compliance 读表；目录不进 BCR；首期只建 `AE`（自贸区作登记机关）；筛查供应商倾向 Sumsub；AE 个人 Client = INDIVIDUAL + `SOLE_ESTABLISHMENT` 属性；资格 REVOKED → Client 自动 SUSPENDED；退回超期 → 案件 EXPIRED、申请 WITHDRAWN。 |
| 落地 | 存量 local / mor-test 数据按**重写**不迁移；Partner 门户案件工作台列后续切片（首期运营代操作并记 `on_behalf_of_partner_id`）；法务待确认 26 项（design.md 9.5a）未确认的国家包行以 DRAFT 入库不发布。 |

## 2. 取代范围（定稿时生效）

- `requirements/20260914/client-console-注册与客户入驻/generic-material-form-proposal.md`：不按国家维度的全局程序 → 辖区化程序与采集 Schema。
- 同目录 `simplification-design.md`：每次重提新任务 → 同案件多轮；退回分区 → 需求级错误。
- `agent-docs/database/CLIENT_CONTRACT_DATABASE.md`、`paytech/client-master/docs/database/*`、`paytech/compliance/docs/database/*`：按 design.md 第 10 节修订。
- `agent-docs/VAULT_SERVICE_ARCHITECTURE.md` 对 CCT 的字段限制：个人证件号例外（需 Vault 数据类型 DML）。
- `2026-10-23-platform-tech-provider-and-partner-payfac.md` 第 5 节 compliance 现状行与 R3：由本决定 ② 落实合规案件部分；交易 / 出款 / 争议 / 价目的外部 PayFac 自操作仍待定。
- `AGENTS.md` 第 84 行已加合规例外（2026-10-27）。

## 3. 后续细化

- 切片顺序：S1 国家包与采集规则 + 手工 CRR + 筛查 Mock + 证件 Vault（合并，因存量重写）→ S1b 入驻码与责任方 → S2 修订申请 → S3 真实筛查 → S4 自动 CRR 与四眼 → S5 持续复审与结算控制 → S6 IDV / 登记库 → S7 PayFac 计费与导出 → S8 Partner / OE 资格复用 → S-P Partner 门户工作台。
- 是否登记到 `agent-docs/README.md` 与 `DOCUMENT_GOVERNANCE.md` 路由表，由用户决定。

package com.paytech.client.master.core.dal.dataobject;

import java.time.Instant;
import java.math.BigDecimal;
import com.paytech.client.master.domain.enums.*;

/** 本库查询的最小持久化投影，不能作为 HTTP 或跨模块 DTO。 */
public final class CheckoutRecords {
    private CheckoutRecords() {
    }

    /** 客户角色的只读字段。 */
    public record Client(
            /** 平台 Client 角色的内部唯一标识。 */
            Long clientId,
            /** 自然人或机构法律身份根的内部唯一标识。 */
            Long partyId,
            /** 当前业务记录在其所属聚合生命周期中的状态代码。允许值：PENDING、ACTIVE、SUSPENDED、TERMINATED。 */
            ClientStatus status) {
    }

    /** 单份经营资料及运营分类确认的只读字段。 */
    public record ClientBusinessProfile(
            /** Client 单份经营资料的稳定标识。 */
            Long businessProfileId,
            /** 平台 Client 角色的内部唯一标识。 */
            Long clientId,
            /** 批准后固定的经营资料摘要；草稿为空。 */
            String contentHash,
            /** 本次分类确认所属的 Risk 任务稳定标识。 */
            Long classificationCaseId,
            /** 确认分类的 Operations Account。 */
            Long classificationConfirmedBy,
            /** 分类确认事实的 UTC 时间。 */
            Instant classificationConfirmedTime) {
    }

    /** 客户经营分类的只读字段。 */
    public record ClientBusinessClassification(
            /** 经营分类行的内部唯一标识。 */
            Long classificationId,
            /** Client 业务资料版本的内部唯一标识。 */
            Long businessProfileId,
            /** 平台定义的经营行业代码。取值来源：平台行业目录；非固定枚举。 */
            String industryCode,
            /** 支付收单语义下的商户类别代码。取值来源：平台采用的 MCC 目录，MCC 必须为四位数字；非固定枚举。 */
            String mccCode,
            /** 用于保证同一 Business Profile 最多存在一组主要经营分类。允许值：1 或 NULL。取值规则：值为 1 时表示一组主要经营分类，其他情况为 NULL。 */
            Integer primarySlot) {
    }

    /** 客户经营主体适用关系的只读字段。 */
    public record ClientEntityAffiliation(
            /** Client 获准在准确交易范围内使用 Operating Entity 的关系标识。 */
            Long affiliationId,
            /** 平台 Client 角色的内部唯一标识。 */
            Long clientId,
            /** CCT唯一不可变OE身份，CHAR(5)，创建时经营国家二位代码加001–999，如AE001；编号不参与路由、不复用。 */
            String entityNo,
            /** 同一业务解析范围内候选记录的确定性顺序，数值越小越优先。 */
            Integer priorityNo,
            /** 当前业务记录在其所属聚合生命周期中的状态代码。允许值：ACTIVE、ENDED。 */
            ClientEntityAffiliationStatus status,
            /** 以 1 或 NULL 实现条件稀疏唯一约束的技术槽位，具体占槽含义由表级规则定义。允许值：1 或 NULL。取值规则：状态为 ACTIVE 时为 1，其他状态为 NULL。 */
            Integer activeSlot,
            /** 业务记录生效区间的 UTC 微秒精度起点，包含该时刻。 */
            Instant effectiveFrom,
            /** 业务记录生效区间的 UTC 微秒精度终点，不包含该时刻；为空表示未预定结束。 */
            Instant effectiveTo) {
    }

    /** 经营主体角色的只读字段。 */
    public record OperatingEntity(
            /** CCT唯一不可变OE身份，CHAR(5)，创建时经营国家二位代码加001–999，如AE001；编号不参与路由、不复用。 */
            String entityNo,
            /** 自然人或机构法律身份根的内部唯一标识。 */
            Long partyId,
            /** 当前业务记录在其所属聚合生命周期中的状态代码。允许值：PENDING、ACTIVE、SUSPENDED、CLOSED。 */
            OperatingEntityStatus status) {
    }

    /** 经营主体来源关系的只读字段。 */
    public record EntitySourceRelation(
            /** Operating Entity 来源关系的稳定内部标识。 */
            Long sourceRelationId,
            /** CCT唯一不可变OE身份，CHAR(5)，创建时经营国家二位代码加001–999，如AE001；编号不参与路由、不复用。 */
            String entityNo,
            /** 该 Operating Entity 来源关系对应的来源主体类型。允许值：PLATFORM、CLIENT、PARTNER。 */
            EntitySourceRelationSourceType sourceType,
            /** 提交并经平台审核关联 Operating Entity 的 Organization Client 标识。 */
            Long sourceClientId,
            /** 提供 Operating Entity 的 Local Partner 标识。 */
            Long sourcePartnerId,
            /**
             * Organization Client 提供 Operating Entity 时的来源认定依据；source_type=CLIENT 时为 SAME_PARTY 或
             * VERIFIED_ASSOCIATION，其他来源为空。允许值：SAME_PARTY、VERIFIED_ASSOCIATION。
             */
            EntitySourceRelationClientSourceBasis clientSourceBasis,
            /** 当前业务记录在其所属聚合生命周期中的状态代码。允许值：ACTIVE、ENDED。 */
            EntitySourceRelationStatus status,
            /** 用于保证同一 Operating Entity 同时只有一个有效来源关系。允许值：1 或 NULL。取值规则：状态为 ACTIVE 时为 1，其他状态为 NULL。 */
            Integer activeSourceSlot,
            /** 业务记录生效区间的 UTC 微秒精度起点，包含该时刻。 */
            Instant effectiveFrom,
            /** 业务记录生效区间的 UTC 微秒精度终点，不包含该时刻；为空表示未预定结束。 */
            Instant effectiveTo) {
    }

    /** 经营主体准入范围的只读字段。 */
    public record OperatingEntityScope(
            /** OE准确四要素范围行的稳定标识，由申请创建，批准后供交易引用。 */
            Long scopeId,
            /** CCT唯一不可变OE身份，CHAR(5)，创建时经营国家二位代码加001–999，如AE001；编号不参与路由、不复用。 */
            String entityNo,
            /** 业务申请记录的内部唯一标识。 */
            Long applicationId,
            /** 准确匹配的消费者国家代码。取值来源：ISO 3166-1 alpha-2；非固定枚举。 */
            String consumerCountryCode,
            /** 准确匹配的交易币种。取值来源：ISO 4217；非固定枚举。 */
            String currency,
            /** 平台定义的经营行业代码。取值来源：平台行业目录；非固定枚举。 */
            String industryCode,
            /** MCC 必须为保留前导零的四位数字字符串，存在于已批准目录。取值来源：平台四位MCC目录；非固定枚举。 */
            String mccCode,
            /** 当前业务记录在其所属聚合生命周期中的状态代码。允许值：DRAFT、ACTIVE、ENDED。 */
            OperatingEntityScopeStatus status,
            /** 以 1 或 NULL 实现条件稀疏唯一约束的技术槽位，具体占槽含义由表级规则定义。允许值：1 或 NULL。取值规则：仅ACTIVE为1，DRAFT和ENDED为NULL。 */
            Integer activeSlot,
            /** 业务记录生效区间的 UTC 微秒精度起点，包含该时刻。 */
            Instant effectiveFrom,
            /** 业务记录生效区间的 UTC 微秒精度终点，不包含该时刻；为空表示未预定结束。 */
            Instant effectiveTo) {
    }

    /** 经营主体入驻申请的只读字段。 */
    public record OperatingEntityApplication(
            /** 业务申请记录的内部唯一标识。 */
            Long applicationId,
            /** 申请创建时冻结的稳定 Operating Entity 标识；ONBOARDING 拒绝或撤回后重提复用同一 PENDING 主体；SCOPE_CHANGE 沿用既有主体且不回退其状态。 */
            String entityNo,
            /** 当前业务记录在其所属聚合生命周期中的状态代码。允许值：DRAFT、SUBMITTED、UNDER_REVIEW、APPROVED、REJECTED、WITHDRAWN。 */
            OperatingEntityApplicationStatus status,
            /** 仅在申请 APPROVED 时回填且匹配申请来源和经营主体的来源关系标识；ONBOARDING 创建或激活来源关系，SCOPE_CHANGE 沿用现有唯一来源关系。 */
            Long approvedSourceRelationId) {
    }

    /** 本地合作伙伴角色的只读字段。 */
    public record LocalPartner(
            /** Local Partner 角色的内部唯一标识。 */
            Long localPartnerId,
            /** 自然人或机构法律身份根的内部唯一标识。 */
            Long partyId,
            /** 当前业务记录在其所属聚合生命周期中的状态代码。允许值：PENDING、ACTIVE、SUSPENDED、TERMINATED。 */
            LocalPartnerStatus status) {
    }

    /** 合同的只读字段。 */
    public record Agreement(
            /** 商业合同稳定身份的内部唯一标识。 */
            Long agreementId,
            /** 区分 Client 合同与 Local Partner 合同的类型代码。允许值：CLIENT、PARTNER。 */
            AgreementAgreementType agreementType,
            /** agreement_type=CLIENT 时必须非空的本库 Client 标识；PARTNER 时必须为空。 */
            Long clientId,
            /** agreement_type=PARTNER 时必须非空的本库 Local Partner 标识；CLIENT 时必须为空。 */
            Long localPartnerId,
            /** CCT唯一不可变OE身份，CHAR(5)，创建时经营国家二位代码加001–999，如AE001；编号不参与路由、不复用。 */
            String entityNo,
            /** 区分新同Party共签与旧逐角色签署合同，创建后不可修改。允许值：MERGED_PARTY、LEGACY_ROLE_SIGNATURE。 */
            AgreementAgreementFormat agreementFormat,
            /** 该独立合同当前所处的法律生命周期状态。允许值：DRAFT、PENDING_SIGNATURE、SIGNED、ACTIVE、EXPIRED、TERMINATED、CANCELLED。 */
            AgreementStatus status,
            /** 业务记录生效区间的 UTC 微秒精度起点，包含该时刻。 */
            Instant effectiveFrom,
            /** 业务记录生效区间的 UTC 微秒精度终点，不包含该时刻；为空表示未预定结束。 */
            Instant effectiveTo,
            /** 合同完成必要签署的 UTC 微秒精度时刻。 */
            Instant signedTime,
            /** 合同正式进入 ACTIVE 状态的 UTC 微秒精度时刻。 */
            Instant activatedTime) {
    }

    /** 合同覆盖经营主体的只读字段。 */
    public record AgreementOperatingEntity(
            /** Partner合同覆盖OE的稳定关联ID；历史Client关联只读保留。 */
            Long agreementOperatingEntityId,
            /** 商业合同稳定身份的内部唯一标识。 */
            Long agreementId,
            /** CCT唯一不可变OE身份，CHAR(5)，创建时经营国家二位代码加001–999，如AE001；编号不参与路由、不复用。 */
            String entityNo) {
    }

    /** 合同参与方与签署的只读字段。 */
    public record AgreementParty(
            /** 合同内一条Party角色记录的稳定标识，同Party允许多角色。 */
            Long agreementPartyId,
            /** 商业合同稳定身份的内部唯一标识。 */
            Long agreementId,
            /** 自然人或机构法律身份根的内部唯一标识。 */
            Long partyId,
            /** 申请或合同冻结引用的 Party 资料版本标识。 */
            Long partyProfileId,
            /** 法律Party在合同中承担的业务角色，不是Party个人或机构类型。允许值：CLIENT、LOCAL_PARTNER、PLATFORM_SIGNATORY、OPERATING_ENTITY。 */
            AgreementPartyPartyRole partyRole,
            /** 签约方完成合同接受时采用的受控方式代码。允许值：E_SIGNATURE、CLICK_ACCEPTANCE、MANUAL_SIGNATURE。 */
            AgreementPartyAcceptanceMethod acceptanceMethod,
            /** 代表签约方完成合同接受的外部 Actor 稳定标识。 */
            Long acceptedByActorId,
            /** 接受事实正式成立的 UTC 微秒精度时刻。 */
            Instant acceptedTime,
            /** 合同接受方式矩阵要求的必填文件引用证据标识。 */
            Long evidenceDocumentId,
            /** 平台生成的全局唯一、稳定合同接受事件编号。 */
            String acceptanceReference,
            /** 未签时全部NULL；组内最小主键行签后为1，其他角色行NULL；禁止同引用跨组复用。允许值：1 或 NULL。取值规则：同合同Party最小角色主键行签后为1，其他角色及未签时为NULL。 */
            Integer acceptanceRefSlot,
            /** 登记最终签署证据的可信Actor，区别于实际签署人。 */
            Long acceptanceRecordedBy,
            /** 首次登记最终签署结果的UTC时间，区别于实际签署时间。 */
            Instant acceptanceRecordedAt) {
    }

    /** 合同文件引用的只读字段。 */
    public record AgreementDocumentRef(
            /** 合同文件引用记录的内部唯一标识。 */
            Long agreementDocumentId,
            /** 商业合同稳定身份的内部唯一标识。 */
            Long agreementId,
            /** 文档用途或法律类别的受控业务代码。允许值：SIGNING_DOCUMENT、ANNEX、EXECUTED_DOCUMENT、TERMINATION_NOTICE。 */
            AgreementDocumentRefDocumentType documentType,
            /** Platform Support 合同文件的逻辑引用，与 content_hash 成组冻结，不建立跨库物理外键。 */
            Long fileId,
            /** 输入为 Platform Support 返回的合同文件 SHA-256，与 file_id 成组冻结。 */
            String contentHash) {
    }

    /** 单份法律主体资料的批准事实，历史签署保持稳定 ID 和摘要。 */
    public record PartyProfile(
            /** Party 资料及其类型明细的内部稳定标识。 */
            Long profileId,
            /** 自然人或机构法律身份根的内部唯一标识。 */
            Long partyId,
            /** 首次批准并永久锁定的 UTC 时间；草稿为空。 */
            Instant approvedTime,
            /** 未批准资料的编辑申请；批准后必须为空。 */
            Long draftApplicationId,
            /** 输入为封存 Party Profile 头、类型明细、Identifier、Address 与 Contact 的规范化业务字段。 */
            String contentHash) {
    }

    /** 商业条款方案的只读字段。 */
    public record CommercialTermPlan(
            /** Client Pricing、Partner Share 或 Platform Service Charge 方案根的内部唯一标识。 */
            Long termPlanId,
            /** 区分客户定价、本地合作伙伴分润与平台服务费的闭合业务类型。允许值：CLIENT_PRICING、PARTNER_SHARE、PLATFORM_SERVICE_CHARGE。 */
            CommercialTermPlanTermType termType,
            /** 商业合同稳定身份的内部唯一标识。 */
            Long agreementId,
            /** CCT唯一不可变OE身份，CHAR(5)，创建时经营国家二位代码加001–999，如AE001；编号不参与路由、不复用。 */
            String entityNo,
            /** 平台 Client 角色的内部唯一标识。 */
            Long clientId,
            /** Local Partner 角色的内部唯一标识。 */
            Long localPartnerId,
            /** 当前业务记录在其所属聚合生命周期中的状态代码。取值来源：Commercial Term Plan 生命周期定义；非固定枚举。 */
            String status) {
    }

    /** 商业条款版本的只读字段。 */
    public record CommercialTermVersion(
            /** 商业条款不可变业务版本的内部唯一标识。 */
            Long termVersionId,
            /** Client Pricing、Partner Share 或 Platform Service Charge 方案根的内部唯一标识。 */
            Long termPlanId,
            /** 商业合同稳定身份的内部唯一标识。 */
            Long agreementId,
            /** 当前业务记录在其所属聚合生命周期中的状态代码。允许值：DRAFT、APPROVED、SCHEDULED、ACTIVE、SUPERSEDED、EXPIRED。 */
            CommercialTermVersionStatus status,
            /** 以 1 或 NULL 实现条件稀疏唯一约束的技术槽位，具体占槽含义由表级规则定义。允许值：1 或 NULL。取值规则：状态为 ACTIVE 时为 1，其他状态为 NULL。 */
            Integer activeSlot,
            /** 业务记录生效区间的 UTC 微秒精度起点，包含该时刻。 */
            Instant effectiveFrom,
            /** 业务记录生效区间的 UTC 微秒精度终点，不包含该时刻；为空表示未预定结束。 */
            Instant effectiveTo) {
    }

    /** 商业条款价格的只读字段。 */
    public record CommercialTermPrice(
            /** Commercial Term Version 下准确 OE 与 Payment Method Profile 价格绑定的内部唯一标识。 */
            Long termPriceId,
            /** 商业条款不可变业务版本的内部唯一标识。 */
            Long termVersionId,
            /** 该价格准确适用且必须被所属 Agreement 覆盖的 Operating Entity；不允许 NULL 或通配值。 */
            String entityNo,
            /**
             * 关联 ACTIVE Payment Method Profile 的全局稳定代码；不得保存或向外传播 Profile 内部 ID。取值来源：CCT ACTIVE Payment Method Profile
             * 代码目录；非固定枚举。
             */
            String paymentMethodProfileCode,
            /** 同一 Commercial Term Version 与准确 OE 下、相同具体度候选的确定性顺序，数值越小越优先。 */
            Integer priorityNo,
            /** Client 合同约定的 T+N 工作日结算到期天数。 */
            Integer settlementBusinessDays,
            /** 计算 Client 合同 T+N 工作日结算到期日使用的受控业务日历代码。取值来源：Finance工作日日历目录；非固定枚举。 */
            String businessCalendarCode,
            /** 解释 Client 合同工作日和截止秒数使用的 IANA 时区代码。取值来源：IANA时区目录，本期仅UTC；非固定枚举。 */
            String settlementTimezone,
            /** 解释 Client 合同结算基准日的本地日内截止秒数，范围为 0 至 86399。 */
            Integer cutoffSecondOfDay) {
    }

    /** 商业条款计算组件的只读字段。 */
    public record CommercialTermComponent(
            /** 商业条款计算组件的内部唯一标识。 */
            Long termComponentId,
            /** Commercial Term Version 下准确 OE 与 Payment Method Profile 价格绑定的内部唯一标识。 */
            Long termPriceId,
            /** 同一商业价格内从 1 开始且连续的稳定计算顺序。 */
            Integer componentNo,
            /** 同一商业价格内唯一的业务组件代码。 */
            String componentCode,
            /** 区分百分比、固定金额与阶梯组件的闭合类型代码。允许值：PERCENTAGE、FIXED、TIERED。 */
            CommercialTermComponentComponentType componentType,
            /** 决定费用或分润组件何时计算的稳定业务事件代码。取值来源：Commercial Term Trigger Catalog；非固定枚举。 */
            String triggerCode,
            /**
             * Client Pricing 使用受控计算基数目录；Partner Share 固定为 ACTUAL_CLIENT_FEE。取值来源：Client Pricing 计算基数目录，Partner Share 固定
             * ACTUAL_CLIENT_FEE；非固定枚举。
             */
            String calculationBase,
            /** 百分比费用、分润或阶梯档位使用的小数费率，取值范围为 0 至 1。 */
            BigDecimal rate,
            /** 固定费用或阶梯档位使用的金额参数；不适用时为空。 */
            BigDecimal fixedAmount,
            /** 固定金额、金额上下限或阶梯边界存在时必填的精确 ISO 4217 币种；纯百分比且无上下限时为空。取值来源：ISO 4217；非固定枚举。 */
            String currency,
            /** 百分比组件计算结果至少收取或分配的最低金额。 */
            BigDecimal minAmount,
            /** 百分比组件计算结果允许收取或分配的最高金额。 */
            BigDecimal maxAmount,
            /** 仅 TIERED 组件非空；WHOLE_BASE 将命中档位参数用于完整基数，PROGRESSIVE 对各档位区间分别计算。允许值：WHOLE_BASE、PROGRESSIVE。 */
            CommercialTermComponentBandMode bandMode) {
    }

    /** 商业条款阶梯档位的只读字段。 */
    public record CommercialTermComponentBand(
            /** 商业条款阶梯组件档位的内部唯一标识。 */
            Long termBandId,
            /** 商业条款计算组件的内部唯一标识。 */
            Long termComponentId,
            /** 同一阶梯组件内从 1 开始且连续的稳定档位顺序。 */
            Integer bandNo,
            /** 阶梯档位适用金额区间的包含下界。 */
            BigDecimal lowerBound,
            /** 档位不包含上界；最后一档必须为空，其他档位必须与下一档下界连续。 */
            BigDecimal upperBound,
            /** 百分比费用、分润或阶梯档位使用的小数费率，取值范围为 0 至 1。 */
            BigDecimal rate,
            /** 固定费用或阶梯档位使用的金额参数；不适用时为空。 */
            BigDecimal fixedAmount) {
    }

    /** 支付方式特征的只读字段。 */
    public record PaymentMethodProfile(
            /** Payment Method Profile 聚合在 CCT 内部使用的唯一标识。 */
            Long paymentMethodProfileId,
            /** CCT 拥有的全局唯一稳定代码；创建后不可修改、不可回收复用，跨聚合和跨系统关联必须使用该代码。 */
            String paymentMethodProfileCode,
            /**
             * 该 Profile 精确绑定的 Payment Method 稳定代码；由 Payment 目录拥有，CCT 不建立 Payment Method 主表。取值来源：Payment 拥有的 Payment
             * Method 稳定代码目录；非固定枚举。
             */
            String paymentMethodCode,
            /** 当前业务记录在其所属聚合生命周期中的状态代码。允许值：DRAFT、ACTIVE、RETIRED。 */
            PaymentMethodProfileStatus status) {
    }

    /** 消费者法律文档的只读字段。 */
    public record ConsumerLegalDocument(
            /** 一份独立、可发布且可审批的消费者法律文档内部唯一标识。 */
            Long legalDocumentId,
            /** 文档用途或法律类别的受控业务代码。允许值：CONSUMER_TERMS、REFUND_POLICY、PRIVACY_NOTICE。 */
            ConsumerLegalDocumentDocumentType documentType,
            /** CCT唯一不可变OE身份，CHAR(5)，创建时经营国家二位代码加001–999，如AE001；编号不参与路由、不复用。 */
            String entityNo,
            /** 消费者法律文档适用的精确 ISO 3166-1 alpha-2 国家代码。取值来源：ISO 3166-1 alpha-2；非固定枚举。 */
            String countryCode,
            /** 内容解析和展示使用的受控 IETF BCP 47 Locale 代码。取值来源：IETF BCP 47 Locale Catalog；非固定枚举。 */
            String locale,
            /** Platform Support 消费者法律文档正文的逻辑引用，与 content_hash 成组冻结，不建立跨库物理外键。 */
            Long contentFileId,
            /** 输入为 Platform Support 返回的消费者法律文档 SHA-256，与 content_file_id 成组冻结。 */
            String contentHash,
            /** 该独立消费者法律文档当前所处的发布生命周期状态。允许值：DRAFT、APPROVED、ACTIVE、EXPIRED、WITHDRAWN。 */
            ConsumerLegalDocumentStatus status,
            /** 业务记录生效区间的 UTC 微秒精度起点，包含该时刻。 */
            Instant effectiveFrom,
            /** 业务记录生效区间的 UTC 微秒精度终点，不包含该时刻；为空表示未预定结束。 */
            Instant effectiveTo,
            /** 法律事实通过批准的 UTC 微秒精度时刻。 */
            Instant approvedTime,
            /** 法律事实正式向消费者发布的 UTC 微秒精度时刻。 */
            Instant publishedTime) {
    }

    /** 法律卖方披露的只读字段。 */
    public record SellerDisclosure(
            /** 一套独立获准公开卖方披露的内部唯一标识。 */
            Long disclosureId,
            /** CCT唯一不可变OE身份，CHAR(5)，创建时经营国家二位代码加001–999，如AE001；编号不参与路由、不复用。 */
            String entityNo,
            /** Seller Disclosure 适用的精确 ISO 3166-1 alpha-2 国家代码。取值来源：ISO 3166-1 alpha-2；非固定枚举。 */
            String countryCode,
            /** 内容解析和展示使用的受控 IETF BCP 47 Locale 代码。取值来源：IETF BCP 47 Locale Catalog；非固定枚举。 */
            String locale,
            /** 该独立法律卖方披露当前所处的发布生命周期状态。允许值：DRAFT、APPROVED、ACTIVE、EXPIRED、WITHDRAWN。 */
            SellerDisclosureStatus status,
            /** 业务记录生效区间的 UTC 微秒精度起点，包含该时刻。 */
            Instant effectiveFrom,
            /** 业务记录生效区间的 UTC 微秒精度终点，不包含该时刻；为空表示未预定结束。 */
            Instant effectiveTo,
            /** 法律事实通过批准的 UTC 微秒精度时刻。 */
            Instant approvedTime,
            /** 法律事实正式向消费者发布的 UTC 微秒精度时刻。 */
            Instant publishedTime) {
    }

    /** 业务行变更历史主档的只读字段。 */
    public record BusinessChangeRecord(
            /** 一条受审计业务行稳定历史身份的内部唯一标识。 */
            Long historyId,
            /**
             * 仅标识已批准接入 BCR
             * 的审计对象类型；不在白名单内的业务表和技术表不得登记。允许值：PARTY、CLIENT、LOCAL_PARTNER、OPERATING_ENTITY、PARTY_PROFILE、CLIENT_BUSINESS_PROFILE、COMMERCIAL_TERM_VERSION、CLIENT_APPLICATION、PARTNER_APPLICATION、OPERATING_ENTITY_APPLICATION、PARTY_RELATION、CLIENT_ENTITY_AFFILIATION、ENTITY_SOURCE_RELATION、AGREEMENT、CONSUMER_LEGAL_DOCUMENT、SELLER_DISCLOSURE、COMMERCIAL_TERM_PLAN、PAYMENT_METHOD_PROFILE。
             */
            BusinessChangeRecordRecordType recordType,
            /** 被审计业务行在其 record_type 下的稳定业务标识。 */
            String recordId,
            /**
             * 该审计对象所属且负责并发串行化的业务聚合根类型；只能使用已批准的 Root
             * 类型。允许值：PARTY、CLIENT、LOCAL_PARTNER、OPERATING_ENTITY、CLIENT_APPLICATION、PARTNER_APPLICATION、OPERATING_ENTITY_APPLICATION、PARTY_RELATION、CLIENT_ENTITY_AFFILIATION、ENTITY_SOURCE_RELATION、AGREEMENT、CONSUMER_LEGAL_DOCUMENT、SELLER_DISCLOSURE、COMMERCIAL_TERM_PLAN、PAYMENT_METHOD_PROFILE。
             */
            BusinessChangeRecordAggregateRootType aggregateRootType,
            /** 该变更记录所属业务聚合根的稳定业务标识。 */
            String aggregateRootId,
            /** 同一聚合内部直接父业务行对应的变更主记录标识。 */
            Long parentHistoryId,
            /** Root 记录中是唯一 expected_change_version CAS 令牌；受审计 Child 中只指向最近一次快照采用的 Root 版本，不执行独立 Child CAS。 */
            Long currentVersion) {
    }
}

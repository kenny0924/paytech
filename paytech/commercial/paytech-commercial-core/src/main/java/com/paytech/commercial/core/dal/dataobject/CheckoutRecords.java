package com.paytech.commercial.core.dal.dataobject;

import java.time.Instant;
import java.math.BigDecimal;
import com.paytech.commercial.domain.enums.*;

/** 本库查询的最小持久化投影，不能作为 HTTP 或跨模块 DTO。 */
public final class CheckoutRecords {
    private CheckoutRecords() {
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

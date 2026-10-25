package com.paytech.client.master.core.dal.dataobject;

import java.time.Instant;
import com.paytech.client.master.api.enums.AcquiringQualification;
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
            OperatingEntityStatus status,
            /** 收单资质档位。允许值：MERCHANT、MERCHANT_RESALE_APPROVED、PAYFAC。 */
            AcquiringQualification acquiringQualification) {
    }

    /** 经营主体来源关系的只读字段。 */
    public record EntitySourceRelation(
            /** Operating Entity 来源关系的稳定内部标识。 */
            Long sourceRelationId,
            /** CCT唯一不可变OE身份，CHAR(5)，创建时经营国家二位代码加001–999，如AE001；编号不参与路由、不复用。 */
            String entityNo,
            /** 该 Operating Entity 来源关系对应的来源主体类型。允许值：CLIENT、PARTNER。 */
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
            Instant publishedTime,
            /** 批准向 Consumer 展示的 Operating Entity 法定名称。 */
            String publicLegalName,
            /** 批准向 Consumer 展示的 Operating Entity 注册编号。 */
            String publicRegistrationNumber,
            /** 按国家披露规则可选展示的卖方税务登记编号。 */
            String publicTaxRegistrationNumber,
            /** 按司法辖区规则批准向 Consumer 披露的卖方地址文本。 */
            String publicAddressText,
            /** 向消费者公开展示的卖方支持邮箱。 */
            String publicSupportEmail,
            /** 向消费者公开展示的卖方支持电话。 */
            String publicSupportPhone) {
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

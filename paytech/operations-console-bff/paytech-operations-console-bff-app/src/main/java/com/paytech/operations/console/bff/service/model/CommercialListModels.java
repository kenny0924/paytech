package com.paytech.operations.console.bff.service.model;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

/**
 * 商务列表与产品目录的页面模型（第二切片 I2：合同列表、方案 / 版本列表、产品目录）
 * <li>列表行与 commercial 列表合同字段一一对应，由 CommercialContractConverter 严格转换：Owner 多出或缺少字段视为合同漂移（502）。</li>
 * <li>查询条件类只在 BFF 内部从 Controller 传到 Client Port，不作为 HTTP Body。</li>
 *
 * @author Claude
 */
public final class CommercialListModels {

    /** 只承载类型，禁止创建实例。 */
    private CommercialListModels() {
    }

    /** 合同列表查询条件；入口已校验格式与分页。 */
    @Getter
    @AllArgsConstructor
    public static class AgreementListRequestBody {
        /** Client ID，可空 */
        private final String clientId;

        /** Local Partner ID，可空 */
        private final String localPartnerId;

        /** 合同类型，可空 */
        private final String agreementType;

        /** 合同状态，可空列表表示不筛选 */
        private final List<String> status;

        /** OE 编号，可空 */
        private final String entityNo;

        /** 合同编号前缀，可空 */
        private final String agreementNo;

        /** 页码，从 1 开始 */
        private final int page;

        /** 每页条数 */
        private final int size;
    }

    /** 方案列表查询条件；入口已校验格式与分页。 */
    @Getter
    @AllArgsConstructor
    public static class TermPlanListRequestBody {
        /** 合同 ID，可空 */
        private final String agreementId;

        /** Client ID，可空 */
        private final String clientId;

        /** Local Partner ID，可空 */
        private final String localPartnerId;

        /** 条款类型，可空 */
        private final String termType;

        /** 方案状态，可空 */
        private final String status;

        /** 页码，从 1 开始 */
        private final int page;

        /** 每页条数 */
        private final int size;
    }

    /** 版本列表查询条件；入口已校验格式与分页。 */
    @Getter
    @AllArgsConstructor
    public static class TermVersionListRequestBody {
        /** 方案 ID，可空 */
        private final String planId;

        /** 合同 ID，可空 */
        private final String agreementId;

        /** 版本状态，可空列表表示不筛选 */
        private final List<String> status;

        /** 查询窗口起点（含），可空 */
        private final String effectiveFrom;

        /** 查询窗口终点（不含），可空 */
        private final String effectiveTo;

        /** 页码，从 1 开始 */
        private final int page;

        /** 每页条数 */
        private final int size;
    }

    /** 合同列表的一行。 */
    @Getter
    @Setter
    public static class AgreementListItem {
        /** 合同 ID */
        private String agreementId;

        /** 合同编号 */
        private String agreementNo;

        /** 合同名称 */
        private String agreementName;

        /** 合同类型：CLIENT、PARTNER */
        private String agreementType;

        /** 合同状态 */
        private String status;

        /** 业务模式；PARTNER 合同为空 */
        private String businessMode;

        /** 资金模式；PARTNER 合同为空 */
        private String fundMode;

        /** Client ID；PARTNER 合同为空 */
        private String clientId;

        /** Local Partner ID；CLIENT 合同为空 */
        private String localPartnerId;

        /** CLIENT 合同的 OE 编号；PARTNER 合同为空 */
        private String entityNo;

        /** 生效起点，UTC ISO 8601 */
        private String effectiveFrom;

        /** 生效终点（不含），为空表示无固定终点 */
        private String effectiveTo;

        /** 签署完成时刻，未签完为空 */
        private String signedTime;

        /** 激活时刻，未激活为空 */
        private String activatedTime;

        /** 合同 BCR Root 当前版本 */
        private Long changeVersion;

        /** 合同行最近更新时间 */
        private String updateTime;
    }

    /** 方案列表的一行：方案字段与当前 ACTIVE / 已排期 SCHEDULED 版本。 */
    @Getter
    @Setter
    public static class TermPlanListItem {
        /** 方案 ID */
        private String planId;

        /** 方案编号 */
        private String planCode;

        /** 条款类型 */
        private String termType;

        /** 合同 ID；Partner 级方案为空 */
        private String agreementId;

        /** OE 编号，可空 */
        private String entityNo;

        /** Client ID，Client 条款非空 */
        private String clientId;

        /** Local Partner ID，Partner 条款非空 */
        private String localPartnerId;

        /** 方案状态：DRAFT、ACTIVE、RETIRED */
        private String status;

        /** 方案 BCR 根当前版本 */
        private String changeVersion;

        /** 当前 ACTIVE 版本 ID，没有为空 */
        private String activeVersionId;

        /** 当前 ACTIVE 版本号，没有为空 */
        private String activeVersionNo;

        /** 当前 ACTIVE 版本生效起点，没有为空 */
        private String activeEffectiveFrom;

        /** 已排期 SCHEDULED 版本 ID，没有为空 */
        private String scheduledVersionId;

        /** 已排期 SCHEDULED 版本号，没有为空 */
        private String scheduledVersionNo;

        /** 已排期 SCHEDULED 版本生效起点，没有为空 */
        private String scheduledEffectiveFrom;

        /** 方案创建时间 */
        private String createTime;

        /** 方案行最近更新时间 */
        private String updateTime;
    }

    /** 版本列表的一行：版本字段、所属方案摘要、Maker / Checker 与价格条数。 */
    @Getter
    @Setter
    public static class TermVersionListItem {
        /** 版本 ID */
        private String versionId;

        /** 方案 ID */
        private String planId;

        /** 方案编号 */
        private String planCode;

        /** 条款类型 */
        private String termType;

        /** 合同 ID；Partner 级方案为空 */
        private String agreementId;

        /** 方案 OE 编号，可空 */
        private String entityNo;

        /** 方案 Client ID，可空 */
        private String clientId;

        /** 方案 Local Partner ID，可空 */
        private String localPartnerId;

        /** 方案内版本号 */
        private String versionNo;

        /** 版本状态 */
        private String status;

        /** 生效起点；排期前为创建时刻占位 */
        private String effectiveFrom;

        /** 生效终点（不含），为空表示无终点 */
        private String effectiveTo;

        /** 舍入方式 */
        private String roundingMode;

        /** 定价模型 */
        private String pricingModel;

        /** 费用税务处理 */
        private String feeTaxTreatment;

        /** 本轮审批的 Maker Account ID，可空 */
        private String makerAccountId;

        /** 本轮审批的 Checker Account ID，可空 */
        private String checkerAccountId;

        /** 本轮提交时刻，可空 */
        private String submittedTime;

        /** 版本内价格条数 */
        private Integer priceCount;

        /** 方案 BCR 根当前版本：批准 / 驳回等写入的 expectedChangeVersion */
        private String changeVersion;

        /** 版本行最近更新时间 */
        private String updateTime;
    }

    /** 产品（L0）列表。 */
    @Getter
    @Setter
    public static class CatalogProductListResponseBody {
        /** 按产品 ID 升序 */
        private List<CatalogProduct> products;
    }

    /** 产品（L0）一行。 */
    @Getter
    @Setter
    public static class CatalogProduct {
        /** 产品 ID */
        private String productId;

        /** 产品代码 */
        private String productCode;

        /** 产品名称 */
        private String productName;

        /** 适用业务模式：MOR、PSP、BOTH */
        private String businessModeScope;

        /** 是否基础产品（随合同默认具备，不单独订阅） */
        private Boolean baseProduct;

        /** 状态：DRAFT、ACTIVE、RETIRED */
        private String status;
    }

    /** 可开通项（L1）列表。 */
    @Getter
    @Setter
    public static class CatalogCapabilityListResponseBody {
        /** 按可开通项 ID 升序 */
        private List<CatalogCapability> capabilities;
    }

    /** 可开通项（L1）一行。 */
    @Getter
    @Setter
    public static class CatalogCapability {
        /** 可开通项 ID */
        private String productCapabilityId;

        /** 所属产品代码 */
        private String productCode;

        /** 可开通项代码 */
        private String capabilityCode;

        /** 可开通项名称 */
        private String capabilityName;

        /** 可开通项种类 */
        private String capabilityKind;

        /** 支付方式代码；非支付方式类为空 */
        private String paymentMethodCode;

        /** 附加维度代码，可空 */
        private String dimensionCode;

        /** 附加维度值，与 dimensionCode 同时出现 */
        private String dimensionValue;

        /** 状态：DRAFT、ACTIVE、RETIRED */
        private String status;
    }

    /** 计费事件（L2）列表。 */
    @Getter
    @Setter
    public static class CatalogFeeEventListResponseBody {
        /** 按计费事件 ID 升序 */
        private List<CatalogFeeEvent> feeEvents;
    }

    /** 计费事件（L2）一行。 */
    @Getter
    @Setter
    public static class CatalogFeeEvent {
        /** 计费事件登记 ID */
        private String productFeeEventId;

        /** 所属产品代码 */
        private String productCode;

        /** 计费事件代码，价格组件的 triggerCode */
        private String feeEventCode;

        /** 登记该事件的条款类型 */
        private String termType;

        /** 计费事实类型 */
        private String billingFactType;
    }
}

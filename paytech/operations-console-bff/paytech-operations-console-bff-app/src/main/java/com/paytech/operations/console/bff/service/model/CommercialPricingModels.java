package com.paytech.operations.console.bff.service.model;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import lombok.Getter;
import lombok.Setter;

/**
 * 定价画像与商业条款（方案 / 版本 / 价格）的页面模型（commercial PricingProfileApi、CommercialTermApi、
 * CommercialTermVersionPriceApi 的 BFF 投影）
 * <li>字段与 commercial 合同一一对应，由 Client 适配器严格转换；Owner 新增字段即视为合同漂移（502）。</li>
 * <li>条款写入不含操作人字段：操作人由 BFF 从会话取得，经 X-Gateway-Actor-Subject 传给 commercial。</li>
 * <li>BFF 只做结构性约束，业务规则与完整格式校验由 commercial 执行，其 422 字段路径投影回本次 Body。</li>
 *
 * @author Claude
 */
public final class CommercialPricingModels {

    /** 只承载类型，禁止创建实例。 */
    private CommercialPricingModels() {
    }

    // ---------- 定价画像 ----------

    /** 创建 DRAFT 定价画像。 */
    @Getter
    @Setter
    public static class CreatePricingProfileRequestBody {
        /** 全局唯一稳定代码 */
        @NotBlank(message = "画像代码必填")
        @Size(max = 64, message = "画像代码最长 64 位")
        private String pricingProfileCode;

        /** 定价对象种类：PAYMENT_METHOD、PAYOUT_RAIL、FX_PAIR、DISPUTE、PERIODIC */
        @NotBlank(message = "画像种类必填")
        private String profileKind;

        /** 展示名称 */
        @NotBlank(message = "画像名称必填")
        @Size(max = 128, message = "画像名称最长 128 字符")
        private String profileName;

        /** Payment Method 代码（PAYMENT_METHOD 必填） */
        private String paymentMethodCode;

        /** 条件行，整体提交，最多 50 条 */
        @NotNull(message = "条件行必填，可为空列表")
        @Size(max = 50, message = "条件行最多 50 条")
        @Valid
        private List<ProfileConditionEntry> conditions;
    }

    /** DRAFT 内整体改写画像。 */
    @Getter
    @Setter
    public static class UpdatePricingProfileRequestBody {
        /** 页面读取到的画像 changeVersion */
        @NotNull(message = "expectedChangeVersion 必填")
        @Positive(message = "expectedChangeVersion 必须为正")
        private Long expectedChangeVersion;

        /** 展示名称 */
        @NotBlank(message = "画像名称必填")
        @Size(max = 128, message = "画像名称最长 128 字符")
        private String profileName;

        /** Payment Method 代码 */
        private String paymentMethodCode;

        /** 条件行，整体提交，最多 50 条 */
        @NotNull(message = "条件行必填，可为空列表")
        @Size(max = 50, message = "条件行最多 50 条")
        @Valid
        private List<ProfileConditionEntry> conditions;
    }

    /** 画像激活或退役的版本条件。 */
    @Getter
    @Setter
    public static class PricingProfileTransitionRequestBody {
        /** 页面读取到的画像 changeVersion */
        @NotNull(message = "expectedChangeVersion 必填")
        @Positive(message = "expectedChangeVersion 必须为正")
        private Long expectedChangeVersion;
    }

    /** 画像的一个等值条件。 */
    @Getter
    @Setter
    public static class ProfileConditionEntry {
        /** 定价维度 */
        @NotBlank(message = "维度必填")
        private String dimensionCode;

        /** 规范化值 */
        @NotBlank(message = "条件值必填")
        @Size(max = 128, message = "条件值最长 128 位")
        private String conditionValue;
    }

    /** 定价画像及其条件。 */
    @Getter
    @Setter
    public static class PricingProfileResponseBody {
        /** 画像 ID */
        private String pricingProfileId;

        /** 画像代码 */
        private String pricingProfileCode;

        /** 展示名称 */
        private String profileName;

        /** 定价对象种类 */
        private String profileKind;

        /** Payment Method 代码；非 PAYMENT_METHOD 为空 */
        private String paymentMethodCode;

        /** 状态：DRAFT、ACTIVE、RETIRED */
        private String status;

        /** 画像当前 changeVersion */
        private Long changeVersion;

        /** 条件行 */
        private List<ProfileConditionDetail> conditions;
    }

    /** 画像列表（不分页，按 ID 升序）。 */
    @Getter
    @Setter
    public static class PricingProfileListResponseBody {
        /** 画像 */
        private List<PricingProfileResponseBody> profiles;
    }

    /** 画像的一个等值条件。 */
    @Getter
    @Setter
    public static class ProfileConditionDetail {
        /** 定价维度 */
        private String dimensionCode;

        /** 规范化值 */
        private String conditionValue;
    }

    // ---------- 商业条款写入 ----------

    /** 创建条款方案（DRAFT）。 */
    @Getter
    @Setter
    public static class CreateCommercialTermPlanRequestBody {
        /** 方案编号，同一条款类型内唯一 */
        @NotBlank(message = "方案编号必填")
        @Size(max = 64, message = "方案编号最长 64 位")
        private String planCode;

        /** 条款类型，例如 CLIENT_PRICING、PARTNER_SHARE */
        @NotBlank(message = "条款类型必填")
        private String termType;

        /** 所属合同 ID */
        @NotBlank(message = "合同 ID 必填")
        private String agreementId;

        /** Client Owner（Client 条款必填） */
        private String clientId;

        /** Local Partner Owner（Partner 条款必填） */
        private String localPartnerId;
    }

    /** 在方案下创建 DRAFT 版本，可复制已有版本。 */
    @Getter
    @Setter
    public static class CreateCommercialTermVersionRequestBody {
        /** 方案当前 changeVersion */
        @NotBlank(message = "expectedChangeVersion 必填")
        private String expectedChangeVersion;

        /** 复制来源版本 ID，可空 */
        private String copyFromVersionId;

        /** 版本冻结的合同 ID（Partner 条款必填） */
        private String agreementId;

        /** 定价模型：BLENDED、INTERCHANGE_PLUS_PLUS；不复制时必填 */
        private String pricingModel;

        /** 费用含税口径：INCLUSIVE、EXCLUSIVE；不复制时必填 */
        private String feeTaxTreatment;
    }

    /** DRAFT 版本价格集合的整体替换。 */
    @Getter
    @Setter
    public static class ReplaceCommercialTermPricesRequestBody {
        /** 方案当前 changeVersion */
        @NotBlank(message = "expectedChangeVersion 必填")
        private String expectedChangeVersion;

        /** 完整价格集合，最多 1000 条；空列表表示清空 */
        @NotNull(message = "价格集合必填，可为空列表")
        @Size(max = 1000, message = "价格最多 1000 条")
        @Valid
        private List<CommercialTermPrice> prices;
    }

    /** 一条价格：准确 OE + 币种 + 画像，含结算四字段与组件。 */
    @Getter
    @Setter
    public static class CommercialTermPrice {
        /** 准确 OE */
        @NotBlank(message = "OE 必填")
        private String entityNo;

        /** 交易币种 */
        @NotBlank(message = "币种必填")
        private String currency;

        /** 定价画像代码 */
        @NotBlank(message = "画像代码必填")
        private String pricingProfileCode;

        /** 同一 OE + 币种内的优先级 */
        @NotNull(message = "优先级必填")
        private Integer priorityNo;

        /** T+N 工作日结算天数（Client 条款必填，Partner 条款为空） */
        private Integer settlementBusinessDays;

        /** 结算工作日日历代码 */
        private String businessCalendarCode;

        /** 结算时区（IANA） */
        private String settlementTimezone;

        /** 结算基准日的本地日内截止秒数 */
        private Integer cutoffSecondOfDay;

        /** 计算组件，至少一项，最多 50 项 */
        @NotEmpty(message = "组件至少一项")
        @Size(max = 50, message = "组件最多 50 项")
        @Valid
        private List<CommercialTermComponent> components;
    }

    /** 价格下的一个计算组件。 */
    @Getter
    @Setter
    public static class CommercialTermComponent {
        /** 价格内唯一的组件代码 */
        @NotBlank(message = "组件代码必填")
        private String componentCode;

        /** 组件类型：PERCENTAGE、FIXED、TIERED */
        @NotBlank(message = "组件类型必填")
        private String componentType;

        /** 触发计费事件 */
        @NotBlank(message = "计费事件必填")
        private String triggerCode;

        /** 计算基数 */
        @NotBlank(message = "计算基数必填")
        private String calculationBase;

        /** 退款时费用处理 */
        @NotBlank(message = "退款处理必填")
        private String refundBehavior;

        /** 费率 */
        private String rate;

        /** 固定金额 */
        private String fixedAmount;

        /** 金额币种 */
        private String currency;

        /** 最低金额 */
        private String minAmount;

        /** 最高金额 */
        private String maxAmount;

        /** 阶梯基数（仅 TIERED） */
        private String bandBasis;

        /** 阶梯模式（仅 TIERED） */
        private String bandMode;

        /** 阶梯档位，最多 50 档 */
        @Size(max = 50, message = "档位最多 50 档")
        @Valid
        private List<CommercialTermBand> bands;
    }

    /** 阶梯组件的一档。 */
    @Getter
    @Setter
    public static class CommercialTermBand {
        /** 包含下界 */
        @NotBlank(message = "档位下界必填")
        private String lowerBound;

        /** 不包含上界；末档为空 */
        private String upperBound;

        /** 档位费率 */
        private String rate;

        /** 档位固定金额 */
        private String fixedAmount;
    }

    /** 版本状态推进（提交、批准、驳回、激活）。 */
    @Getter
    @Setter
    public static class CommercialTermVersionCommandRequestBody {
        /** 方案当前 changeVersion */
        @NotBlank(message = "expectedChangeVersion 必填")
        private String expectedChangeVersion;
    }

    /** 已批准版本排期。 */
    @Getter
    @Setter
    public static class ScheduleCommercialTermVersionRequestBody {
        /** 方案当前 changeVersion */
        @NotBlank(message = "expectedChangeVersion 必填")
        private String expectedChangeVersion;

        /** 生效起点，UTC RFC 3339 */
        @NotBlank(message = "生效起点必填")
        private String effectiveFrom;
    }

    // ---------- 商业条款读取 ----------

    /** 商业条款方案。 */
    @Getter
    @Setter
    public static class CommercialTermPlanResponseBody {
        /** 方案 ID */
        private String planId;

        /** 方案编号 */
        private String planCode;

        /** 条款类型 */
        private String termType;

        /** CLIENT 合同 ID；Partner 条款为空 */
        private String agreementId;

        /** CLIENT 合同 OE；Partner 条款为空 */
        private String entityNo;

        /** Client Owner */
        private String clientId;

        /** Local Partner Owner */
        private String localPartnerId;

        /** 方案状态 */
        private String status;

        /** 方案当前 changeVersion */
        private String changeVersion;
    }

    /** 商业条款版本概要。 */
    @Getter
    @Setter
    public static class CommercialTermVersionResponseBody {
        /** 版本 ID */
        private String versionId;

        /** 方案 ID */
        private String planId;

        /** 条款类型 */
        private String termType;

        /** 版本冻结的合同 ID */
        private String agreementId;

        /** 方案内版本号 */
        private String versionNo;

        /** 状态 */
        private String status;

        /** 生效起点；排期前为占位时刻 */
        private String effectiveFrom;

        /** 生效终点（不含） */
        private String effectiveTo;

        /** 舍入方式 */
        private String roundingMode;

        /** 定价模型 */
        private String pricingModel;

        /** 费用含税口径 */
        private String feeTaxTreatment;

        /** 方案当前 changeVersion */
        private String changeVersion;
    }

    /** 固定版本下某 OE 的价格全集。 */
    @Getter
    @Setter
    public static class CommercialTermVersionPricesResponseBody {
        /** 版本 ID */
        private String termVersionId;

        /** 条款类型 */
        private String termType;

        /** 准确 OE */
        private String entityNo;

        /** 价格，按币种、优先级排序 */
        private List<CommercialPrice> prices;
    }

    /** 不可变价格对象。 */
    @Getter
    @Setter
    public static class CommercialPrice {
        /** 条款类型 */
        private String termType;

        /** 版本冻结的合同 ID */
        private String agreementId;

        /** 方案 ID */
        private String planId;

        /** 版本 ID */
        private String termVersionId;

        /** 准确 OE */
        private String entityNo;

        /** Client Owner */
        private String clientId;

        /** Local Partner Owner */
        private String localPartnerId;

        /** 价格 ID */
        private String priceId;

        /** 定价画像代码 */
        private String pricingProfileCode;

        /** 画像绑定的 Payment Method */
        private String paymentMethodCode;

        /** 优先级 */
        private Integer priorityNo;

        /** 交易币种 */
        private String currency;

        /** 定价模型 */
        private String pricingModel;

        /** 费用含税口径 */
        private String feeTaxTreatment;

        /** 画像条件 */
        private List<CommercialPriceCondition> conditions;

        /** 计算组件 */
        private List<CommercialPriceComponent> components;

        /** 结算四字段；Partner 条款为 null */
        private CommercialPriceSettlement settlement;
    }

    /** 画像的一条等值条件。 */
    @Getter
    @Setter
    public static class CommercialPriceCondition {
        /** 维度代码 */
        private String dimensionCode;

        /** 条件值 */
        private String conditionValue;
    }

    /** 价格的一个计算组件。 */
    @Getter
    @Setter
    public static class CommercialPriceComponent {
        /** 组件 ID */
        private String componentId;

        /** 组件序号 */
        private Integer componentNo;

        /** 组件代码 */
        private String componentCode;

        /** 组件类型 */
        private String componentType;

        /** 触发计费事件 */
        private String triggerCode;

        /** 计算基数 */
        private String calculationBase;

        /** 退款时费用处理 */
        private String refundBehavior;

        /** 费率 */
        private String rate;

        /** 固定金额 */
        private String fixedAmount;

        /** 金额币种 */
        private String currency;

        /** 最低金额 */
        private String minAmount;

        /** 最高金额 */
        private String maxAmount;

        /** 阶梯基数 */
        private String bandBasis;

        /** 阶梯模式 */
        private String bandMode;

        /** 档位 */
        private List<CommercialPriceBand> bands;
    }

    /** 阶梯档位。 */
    @Getter
    @Setter
    public static class CommercialPriceBand {
        /** 档位 ID */
        private String bandId;

        /** 档位序号 */
        private Integer bandNo;

        /** 包含下界 */
        private String lowerBound;

        /** 不包含上界 */
        private String upperBound;

        /** 档位费率 */
        private String rate;

        /** 档位固定金额 */
        private String fixedAmount;

        /** 币种 */
        private String currency;
    }

    /** Client 条款结算输入。 */
    @Getter
    @Setter
    public static class CommercialPriceSettlement {
        /** T+N 工作日结算天数 */
        private Integer settlementBusinessDays;

        /** 工作日日历代码 */
        private String businessCalendarCode;

        /** 结算时区 */
        private String settlementTimezone;

        /** 截止秒数 */
        private Integer cutoffSecondOfDay;
    }

    /**
     * 条款版本编辑页聚合：版本概要、所属合同范围与版本下全部 OE 的价格
     * <li>合同范围给出可定价的 OE（CLIENT 为合同 OE，PARTNER 为覆盖 OE）与开通项，供价格矩阵与覆盖提示使用。</li>
     * <li>价格按 OE 逐个读取后合并，按 OE、币种、优先级排序。</li>
     */
    @Getter
    @Setter
    public static class CommercialTermWorkspaceResponseBody {
        /** 版本概要 */
        private CommercialTermVersionResponseBody version;

        /** 版本冻结合同的定价范围 */
        private TermAgreementScope agreement;

        /** 版本下全部 OE 的价格 */
        private List<CommercialPrice> prices;
    }

    /** 版本冻结合同的定价范围。 */
    @Getter
    @Setter
    public static class TermAgreementScope {
        /** 合同 ID */
        private String agreementId;

        /** 合同编号 */
        private String agreementNo;

        /** 合同名称 */
        private String agreementName;

        /** 合同类型 */
        private String agreementType;

        /** 合同状态 */
        private String status;

        /** Client ID；PARTNER 为空 */
        private String clientId;

        /** Local Partner ID；CLIENT 为空 */
        private String localPartnerId;

        /** 可定价 OE，升序 */
        private List<String> entityNos;

        /** 合同开通项（含约束），用于覆盖提示 */
        private List<CommercialAgreementModels.AgreementCapabilityDetail> capabilities;
    }
}

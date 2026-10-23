package com.paytech.operations.console.bff.service.model;

import java.util.List;
import java.util.Map;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import com.paytech.operations.console.bff.service.model.CommercialPricingModels.CommercialPrice;
import com.paytech.operations.console.bff.service.model.CommercialPricingModels.CommercialPriceComponent;
import com.paytech.operations.console.bff.service.model.CommercialPricingModels.CommercialPriceCondition;
import com.paytech.operations.console.bff.service.model.CommercialPricingModels.CommercialPriceSettlement;

import lombok.Getter;
import lombok.Setter;

/**
 * 费率卡预览（F6）与价格试算（F7）的页面模型
 * <li>费率卡与 commercial RateCardApi 字段一一对应，由 Client 适配器严格转换；Owner 新增字段即视为合同漂移（502）。</li>
 * <li>试算请求只承载运营输入的事实；BFF 补足追溯 ID 后转发 commercial 价格命中，示例费用由 BFF 按命中价格计算，仅供参考。</li>
 *
 * @author Claude
 */
public final class CommercialPricePreviewModels {

    /** 只承载类型，禁止创建实例。 */
    private CommercialPricePreviewModels() {
    }

    // ---------- 费率卡 ----------

    /** Client 结构化费率卡：ACTIVE 合同、开通项与当前 / 已排期版本的价格。 */
    @Getter
    @Setter
    public static class RateCardResponseBody {
        /** Client ID */
        private String clientId;

        /** 费率卡时点（UTC RFC 3339） */
        private String asOf;

        /** 币种筛选，未筛选为空 */
        private String currency;

        /** ACTIVE 且在时点生效的 CLIENT 合同，按合同 ID 升序 */
        private List<AgreementRateCard> agreements;
    }

    /** 一份合同的费率卡。 */
    @Getter
    @Setter
    public static class AgreementRateCard {
        /** 合同 ID */
        private String agreementId;

        /** 合同编号 */
        private String agreementNo;

        /** 合同 OE */
        private String entityNo;

        /** 业务模式 */
        private String businessMode;

        /** 资金模式 */
        private String fundMode;

        /** 定价权 */
        private String pricingAuthority;

        /** 合同生效起点 */
        private String effectiveFrom;

        /** 合同生效终点 */
        private String effectiveTo;

        /** 订阅产品代码 */
        private List<String> products;

        /** 开通项（含约束与保证金） */
        private List<CapabilityRateCard> capabilities;

        /** 当前与已排期版本的价格 */
        private List<TermVersionRateCard> termVersions;
    }

    /** 一个开通项。 */
    @Getter
    @Setter
    public static class CapabilityRateCard {
        /** 开通项 ID */
        private String agreementCapabilityId;

        /** 可开通项代码 */
        private String capabilityCode;

        /** 所属产品 */
        private String productCode;

        /** 可开通项种类 */
        private String capabilityKind;

        /** 支付方式 */
        private String paymentMethodCode;

        /** 细分维度 */
        private String dimensionCode;

        /** 细分维度值 */
        private String dimensionValue;

        /** 准确 OE */
        private String entityNo;

        /** 开通状态 */
        private String status;

        /** 开通生效起点 */
        private String effectiveFrom;

        /** 开通生效终点 */
        private String effectiveTo;

        /** 滚动保证金比例 */
        private String rollingReserveRate;

        /** 滚动保证金天数 */
        private Integer rollingReserveDays;

        /** 接受条件行（limitCode 为维度代码） */
        private List<CapabilityLimit> limits;

        /** 是否要求 3DS 认证 */
        private Boolean require3ds;
    }

    /** 开通项接受条件的一个取值。 */
    @Getter
    @Setter
    public static class CapabilityLimit {
        /** 维度代码 */
        private String limitCode;

        /** 约束值 */
        private String limitValue;
    }

    /** 一个条款版本的价格。 */
    @Getter
    @Setter
    public static class TermVersionRateCard {
        /** 方案 ID */
        private String planId;

        /** 条款类型 */
        private String termType;

        /** 版本 ID */
        private String versionId;

        /** 版本号 */
        private String versionNo;

        /** 版本状态 */
        private String status;

        /** CURRENT：时点适用版本；SCHEDULED：时点之后生效的已排期版本 */
        private String timing;

        /** 生效起点 */
        private String effectiveFrom;

        /** 生效终点 */
        private String effectiveTo;

        /** 定价模型 */
        private String pricingModel;

        /** 费用含税口径 */
        private String feeTaxTreatment;

        /** 每个开通项下按币种分块的价格 */
        private List<CapabilityPrices> capabilities;
    }

    /** 一个开通项在某版本下的价格。 */
    @Getter
    @Setter
    public static class CapabilityPrices {
        /** 开通项 ID */
        private String agreementCapabilityId;

        /** 可开通项代码 */
        private String capabilityCode;

        /** 按币种分块 */
        private List<CurrencyPrices> currencies;
    }

    /** 一个币种的价格块。 */
    @Getter
    @Setter
    public static class CurrencyPrices {
        /** 币种 */
        private String currency;

        /** 适用画像，具体度降序、priorityNo 升序 */
        private List<ProfilePrice> profiles;
    }

    /** 一个画像的价格。 */
    @Getter
    @Setter
    public static class ProfilePrice {
        /** 价格 ID */
        private String priceId;

        /** 画像代码 */
        private String pricingProfileCode;

        /** 画像名称 */
        private String profileName;

        /** 具体度：不同条件维度数 */
        private int specificity;

        /** 优先级 */
        private Integer priorityNo;

        /** 画像条件 */
        private List<CommercialPriceCondition> conditions;

        /** 结算四字段 */
        private CommercialPriceSettlement settlement;

        /** 按计费事件分组的组件 */
        private List<FeeEventComponents> feeEvents;
    }

    /** 一个计费事件下的组件。 */
    @Getter
    @Setter
    public static class FeeEventComponents {
        /** 计费事件代码 */
        private String feeEventCode;

        /** 组件，按 componentNo 排序 */
        private List<CommercialPriceComponent> components;
    }

    // ---------- 价格试算 ----------

    /**
     * 价格试算：在一个条款版本内按运营输入的事实命中价格，并可给出示例金额的费用
     * <li>字段含义与 commercial 价格命中一致；BFF 只做结构性约束，父链、时点与事实合法性由 commercial 判定。</li>
     */
    @Getter
    @Setter
    public static class SimulateCommercialPriceRequestBody {
        /** 条款版本 ID */
        @NotBlank(message = "条款版本 ID 必填")
        @Pattern(regexp = "[1-9][0-9]{0,18}", message = "条款版本 ID 是正十进制整数")
        private String termVersionId;

        /** 准确 OE */
        @NotBlank(message = "OE 必填")
        @Pattern(regexp = "[A-Z]{2}[0-9]{3}", message = "OE 格式为两位大写字母加三位数字")
        private String entityNo;

        /** 版本冻结的合同 ID */
        @NotBlank(message = "合同 ID 必填")
        @Pattern(regexp = "[1-9][0-9]{0,18}", message = "合同 ID 是正十进制整数")
        private String agreementId;

        /** 条款类型，必须等于版本所属方案的条款类型 */
        @NotBlank(message = "条款类型必填")
        @Pattern(regexp = "[A-Z][A-Z0-9_]{0,63}", message = "条款类型格式不正确")
        private String termType;

        /** 交易币种 */
        @NotBlank(message = "币种必填")
        @Pattern(regexp = "[A-Z]{3}", message = "币种为三位大写字母")
        private String currency;

        /** 商业时点（UTC RFC 3339） */
        @NotBlank(message = "商业时点必填")
        @Pattern(regexp = "\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(\\.\\d{1,6})?Z", message = "商业时点必须为 UTC RFC 3339")
        private String contextAsOfTime;

        /** Client Owner；Client 条款必填 */
        @Pattern(regexp = "[1-9][0-9]{0,18}", message = "Client ID 是正十进制整数")
        private String clientId;

        /** Local Partner Owner；Partner 条款必填 */
        @Pattern(regexp = "[1-9][0-9]{0,18}", message = "Local Partner ID 是正十进制整数")
        private String localPartnerId;

        /** 受控定价事实 */
        @NotNull(message = "定价事实必填")
        @Valid
        private SimulationPricingFacts paymentFacts;

        /**
         * 示例交易金额（币种主单位十进制字符串）
         * <li>为空时只返回命中价格，不计算示例费用。</li>
         */
        @Pattern(regexp = "(0|[1-9][0-9]{0,14})(\\.[0-9]{1,6})?", message = "示例金额为非负十进制字符串")
        private String amount;
    }

    /** 试算的定价事实：支付方式与维度事实（键为 PricingDimension 代码，值为 null 表示确认不适用）。 */
    @Getter
    @Setter
    public static class SimulationPricingFacts {
        /** 支付方式稳定代码 */
        @Pattern(regexp = "[A-Z][A-Z0-9_]{0,63}", message = "支付方式代码格式不正确")
        private String paymentMethodCode;

        /** 维度事实 */
        @NotNull(message = "维度事实必填，可为空对象")
        @Size(max = 32, message = "维度事实最多 32 项")
        private Map<String, String> dimensions;
    }

    /** 价格试算结果：命中价格与可选的示例费用。 */
    @Getter
    @Setter
    public static class CommercialPriceSimulationResponseBody {
        /** commercial 命中的不可变价格 */
        private CommercialPrice price;

        /** 示例费用；未提供示例金额时为 null */
        private FeeIllustration illustration;
    }

    /**
     * 按命中价格计算的示例费用（BFF 计算，仅供参考，不是计费结果）
     * <li>百分比 = 基数 × 费率 + 固定额；固定 = 固定额；阶梯按 TRANSACTION_AMOUNT：WHOLE_BASE 用命中档参数作用于完整基数，
     * PROGRESSIVE 各档区间分别乘档位费率后相加；再按组件最低 / 最高金额约束，最后按币种精度 HALF_UP 归整。</li>
     * <li>计算基数取示例金额；ACTUAL_CLIENT_FEE、BUY_RATE_DIFFERENCE 与 PERIOD_VOLUME / RETROACTIVE 阶梯无法由单笔金额得到，标为未计算。</li>
     */
    @Getter
    @Setter
    public static class FeeIllustration {
        /** 示例金额 */
        private String amount;

        /** 币种 */
        private String currency;

        /** 币种精度（小数位） */
        private int fractionDigits;

        /** 按计费事件分组的结果，按事件代码排序 */
        private List<FeeEventIllustration> feeEvents;
    }

    /** 一个计费事件的示例费用。 */
    @Getter
    @Setter
    public static class FeeEventIllustration {
        /** 计费事件代码 */
        private String feeEventCode;

        /** 已计算组件的合计；全部未计算时为 null */
        private String totalAmount;

        /** 组件，按 componentNo 排序 */
        private List<ComponentIllustration> components;
    }

    /** 一个组件的示例计算。 */
    @Getter
    @Setter
    public static class ComponentIllustration {
        /** 组件序号 */
        private Integer componentNo;

        /** 组件代码 */
        private String componentCode;

        /** 组件类型 */
        private String componentType;

        /** 计算基数 */
        private String calculationBase;

        /** 是否已计算 */
        private boolean computed;

        /** 约束前金额（8 位小数）；未计算为 null */
        private String rawAmount;

        /** 归整后的示例费用；未计算为 null */
        private String amount;

        /**
         * 计算说明，按出现顺序：已计算时为命中档位、累进档数、上下限生效；未计算时只有一条未计算原因。
         * 前端按 code 翻译，params 为翻译参数。
         */
        private List<IllustrationNote> notes;
    }

    /**
     * 示例费用的一条说明
     *
     * @param code 说明编码
     * @param params 翻译参数（字符串），各编码的参数见 {@link IllustrationNoteCode}
     */
    public record IllustrationNote(IllustrationNoteCode code, Map<String, String> params) {
    }

    /** 示例费用说明编码；括号内为 params 的键。 */
    public enum IllustrationNoteCode {
        /** WHOLE_BASE 命中档位（bandNo） */
        BAND_HIT,

        /** WHOLE_BASE 没有命中的档位 */
        BAND_NONE,

        /** PROGRESSIVE 参与累进的档数（count） */
        PROGRESSIVE_BANDS,

        /** 按最低金额（amount） */
        MIN_APPLIED,

        /** 按最高金额（amount） */
        MAX_APPLIED,

        /** 未计算：计算基数依赖其他费用结果（base） */
        BASE_DEPENDS_ON_FEE,

        /** 未计算：组件类型不支持示例计算（type） */
        COMPONENT_UNSUPPORTED,

        /** 未计算：阶梯基数依赖周期累计量（basis） */
        BAND_BASIS_PERIODIC,

        /** 未计算：阶梯模式不支持单笔示例计算（mode） */
        BAND_MODE_UNSUPPORTED
    }
}

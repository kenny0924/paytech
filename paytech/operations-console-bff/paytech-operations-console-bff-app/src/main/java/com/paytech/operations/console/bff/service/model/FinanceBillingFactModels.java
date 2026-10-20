package com.paytech.operations.console.bff.service.model;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import lombok.Getter;
import lombok.Setter;

/**
 * 计费事实人工登记页面模型（FIN-10 计费事实）
 * <li>响应字段与 billing BillingFactQueryApi / BillingFactCommandApi 合同一一对应，由 Client 适配器严格转换。</li>
 * <li>登记请求不含 sourceSystem 与 sourceReference：BFF 固定 sourceSystem=OPERATIONS 并为每次登记生成 OPS- + UUID；编制人取自会话。</li>
 *
 * @author Claude
 */
public final class FinanceBillingFactModels {
    /** Operating Entity 编号格式，与 billing 合同一致。 */
    public static final String ENTITY_NO_PATTERN = "[A-Z]{2}(00[1-9]|0[1-9][0-9]|[1-9][0-9]{2})";

    /** 只承载类型，禁止创建实例。 */
    private FinanceBillingFactModels() {
    }

    /** 计费事实管理列表元素（事实头摘要）。 */
    @Getter
    @Setter
    public static class BillingFactSummaryResponseBody {
        /** 计费事实 ID。 */
        private String factId;

        /** 事实类型。 */
        private String factType;

        /** 来源引用；Operations 登记为 OPS- + UUID。 */
        private String sourceReference;

        /** Client ID；可为空。 */
        private String clientId;

        /** 登记状态：DRAFT、APPROVED、SUPERSEDED 或 VOIDED。 */
        private String status;

        /** 总额，按币种精度补齐。 */
        private String grossAmount;

        /** 编制人 Account ID。 */
        private String makerId;

        /** 复核人 Account ID；未批准为空。 */
        private String checkerId;

        /** 登记时间（UTC）。 */
        private Instant createTime;

        /** 批准时间（UTC）；未批准为空。 */
        private Instant approvedTime;
    }

    /** 计费事实详情：事实头、登记与批准信息、发布状态、定价输入、外部引用、明细行与行链接。 */
    @Getter
    @Setter
    public static class BillingFactResponseBody {
        /** 计费事实 ID。 */
        private String factId;

        /** 事实版本。 */
        private String factVersion;

        /** 被替代事实 ID；可为空。 */
        private String supersedesFactId;

        /** 来源事实 ID；可为空。 */
        private String parentFactId;

        /** 事实类型。 */
        private String factType;

        /** 来源类型。 */
        private String originType;

        /** 登记状态：DRAFT、APPROVED、SUPERSEDED 或 VOIDED。 */
        private String status;

        /** 来源系统。 */
        private String sourceSystem;

        /** 来源事件键（来源引用）。 */
        private String sourceEventKey;

        /** 来源版本。 */
        private String sourceVersion;

        /** 来源内容摘要。 */
        private String sourcePayloadHash;

        /** 业务时间（UTC）；批准后为批准时间。 */
        private Instant businessTime;

        /** 登记时间（UTC）。 */
        private Instant recordedTime;

        /** 余额可用时间（UTC）；可为空。 */
        private Instant balanceAvailableTime;

        /** Client ID；可为空。 */
        private String clientId;

        /** Operating Entity 编号。 */
        private String entityNo;

        /** Local Partner ID；可为空。 */
        private String localPartnerId;

        /** Finance Entity ID；可为空。 */
        private String financeEntityId;

        /** 币种。 */
        private String currency;

        /** 总额，按币种精度补齐。 */
        private String grossAmount;

        /** 编制人 Account ID。 */
        private String makerId;

        /** 复核人 Account ID；未批准为空。 */
        private String checkerId;

        /** 批准时间（UTC）；未批准为空。 */
        private Instant approvedTime;

        /** 发布时间（UTC）；未发布为空。 */
        private Instant publishedAt;

        /** 定价输入；仅 PAYMENT 事实，本期恒为空。 */
        private BillingFactPricingResponseBody pricing;

        /** 外部引用。 */
        private List<BillingFactRefResponseBody> refs;

        /** 明细行。 */
        private List<BillingFactLineResponseBody> lines;

        /** 行链接。 */
        private List<BillingFactLineLinkResponseBody> links;
    }

    /** 定价输入。 */
    @Getter
    @Setter
    public static class BillingFactPricingResponseBody {
        /** Client 合同 ID。 */
        private String clientAgreementId;

        /** 合作方合同 OE ID。 */
        private String partnerAgreementOeId;

        /** 商务上下文时间（UTC）。 */
        private Instant commercialContextTime;

        /** 消费者国家代码。 */
        private String consumerCountryCode;

        /** 支付方式。 */
        private String paymentMethod;

        /** 行业代码。 */
        private String industryCode;

        /** MCC。 */
        private String mccCode;

        /** 卡组织。 */
        private String cardNetwork;

        /** 资金类型。 */
        private String fundingType;

        /** 卡等级。 */
        private String cardLevel;

        /** 认证类别。 */
        private String authenticationClass;

        /** 商务价格 ID。 */
        private String commercialPriceId;

        /** 结算工作日数。 */
        private String settlementBusinessDays;

        /** 营业日历代码。 */
        private String businessCalendarCode;

        /** 结算时区。 */
        private String settlementTimezone;

        /** 截止时间（日内秒数）。 */
        private String cutoffSecondOfDay;
    }

    /** 外部引用。 */
    @Getter
    @Setter
    public static class BillingFactRefResponseBody {
        /** 引用类型。 */
        private String refType;

        /** 引用标识。 */
        private String refId;
    }

    /** 明细行及其计算证据。 */
    @Getter
    @Setter
    public static class BillingFactLineResponseBody {
        /** 行号。 */
        private String lineNo;

        /** 金额组件类型。 */
        private String componentType;

        /** 付方角色。 */
        private String payerRole;

        /** 付方标识。 */
        private String payerId;

        /** 收方角色。 */
        private String payeeRole;

        /** 收方标识。 */
        private String payeeId;

        /** 方向：CHARGE、REVERSAL 或 ADJUSTMENT。 */
        private String direction;

        /** 入账金额，按币种精度补齐。 */
        private String postedAmount;

        /** 目标余额桶；可为空。 */
        private String targetBucket;

        /** 计算基数；人工行为空。 */
        private String basisAmount;

        /** 费率；人工行为空。 */
        private String rateValue;

        /** 舍入前金额；人工行为空。 */
        private String rawAmount;

        /** 来源规则类型。 */
        private String sourceRuleType;

        /** 来源规则版本 ID。 */
        private String sourceRuleVersionId;

        /** 来源价格 ID。 */
        private String sourcePriceId;

        /** 来源规则组件 ID。 */
        private String sourceRuleComponentId;

        /** 来源证据。 */
        private Map<String, Object> sourceEvidence;
    }

    /** 行链接。 */
    @Getter
    @Setter
    public static class BillingFactLineLinkResponseBody {
        /** 来源行 ID。 */
        private String sourceLineId;

        /** 目标行 ID。 */
        private String targetLineId;

        /** 链接类型。 */
        private String linkType;

        /** 链接金额。 */
        private String linkedAmount;
    }

    /** 登记计费事实（DRAFT）；sourceSystem 与 sourceReference 由 BFF 填入。 */
    @Getter
    @Setter
    public static class SubmitBillingFactRequestBody {
        /** 事实类型：MANUAL_ADJUSTMENT 或 COST_ADJUSTMENT。 */
        @NotBlank(message = "事实类型必填")
        @Pattern(regexp = "MANUAL_ADJUSTMENT|COST_ADJUSTMENT", message = "事实类型只能为 MANUAL_ADJUSTMENT 或 COST_ADJUSTMENT")
        private String factType;

        /** 来源事实 ID；可为空。 */
        @Pattern(regexp = "[1-9][0-9]{0,18}", message = "来源事实 ID 必须是正十进制整数字符串")
        private String parentFactId;

        /** 被替代事实 ID；可为空，必须是 APPROVED 事实。 */
        @Pattern(regexp = "[1-9][0-9]{0,18}", message = "被替代事实 ID 必须是正十进制整数字符串")
        private String supersedesFactId;

        /** Client ID；可为空。 */
        @Pattern(regexp = "[1-9][0-9]{0,18}", message = "Client ID 必须是正十进制整数字符串")
        private String clientId;

        /** Operating Entity 编号。 */
        @NotBlank(message = "主体必填")
        @Pattern(regexp = ENTITY_NO_PATTERN, message = "主体必须是 entityNo 格式")
        private String entityNo;

        /** Local Partner ID；可为空。 */
        @Pattern(regexp = "[1-9][0-9]{0,18}", message = "Local Partner ID 必须是正十进制整数字符串")
        private String localPartnerId;

        /** Finance Entity ID；可为空。 */
        @Pattern(regexp = "[1-9][0-9]{0,18}", message = "Finance Entity ID 必须是正十进制整数字符串")
        private String financeEntityId;

        /** 币种。 */
        @NotBlank(message = "币种必填")
        @Pattern(regexp = "[A-Z]{3}", message = "币种必须是大写 ISO 4217 三位代码")
        private String currency;

        /** 外部引用（如工单号）；最多 10 个。 */
        @Size(max = 10, message = "外部引用最多 10 个")
        private List<@Valid @NotNull BillingFactRefRequestBody> refs;

        /** 明细行；1 至 100 行。 */
        @NotNull(message = "明细行必填")
        @Size(min = 1, max = 100, message = "明细行为 1 至 100 行")
        private List<@Valid @NotNull BillingFactLineRequestBody> lines;
    }

    /** 登记用外部引用。 */
    @Getter
    @Setter
    public static class BillingFactRefRequestBody {
        /** 引用类型。 */
        @NotBlank(message = "引用类型必填")
        @Pattern(regexp = "[A-Z][A-Z_]{0,31}", message = "引用类型必须是大写字母开头的大写字母或下划线，最长 32 位")
        private String refType;

        /** 引用标识。 */
        @NotBlank(message = "引用标识必填")
        @Pattern(regexp = "[A-Za-z0-9][A-Za-z0-9._-]{0,63}", message = "引用标识必须是字母或数字开头的安全 ASCII 文本，最长 64 位")
        private String refId;
    }

    /** 登记用明细行。 */
    @Getter
    @Setter
    public static class BillingFactLineRequestBody {
        /** 金额组件类型。 */
        @NotBlank(message = "金额组件必填")
        @Pattern(regexp = "[A-Z][A-Z0-9_]{0,63}", message = "金额组件必须是大写字母开头的大写字母、数字或下划线，最长 64 位")
        private String componentType;

        /** 付方角色。 */
        @NotBlank(message = "付方角色必填")
        @Size(max = 64, message = "付方角色不超过 64 个字符")
        private String payerRole;

        /** 付方标识：entityNo 或正十进制整数字符串。 */
        @NotBlank(message = "付方标识必填")
        @Pattern(regexp = "[A-Z0-9]{1,19}", message = "付方标识必须是 entityNo 或正十进制整数字符串")
        private String payerId;

        /** 收方角色。 */
        @NotBlank(message = "收方角色必填")
        @Size(max = 64, message = "收方角色不超过 64 个字符")
        private String payeeRole;

        /** 收方标识：entityNo 或正十进制整数字符串。 */
        @NotBlank(message = "收方标识必填")
        @Pattern(regexp = "[A-Z0-9]{1,19}", message = "收方标识必须是 entityNo 或正十进制整数字符串")
        private String payeeId;

        /** 金额：主货币单位十进制字符串。 */
        @NotBlank(message = "金额必填")
        @Pattern(regexp = "(0|[1-9][0-9]{0,15})(\\.[0-9]{1,8})?", message = "金额必须是主货币单位的普通十进制字符串")
        private String amount;

        /** 方向：CHARGE、REVERSAL 或 ADJUSTMENT。 */
        @NotBlank(message = "方向必填")
        @Pattern(regexp = "CHARGE|REVERSAL|ADJUSTMENT", message = "方向只能为 CHARGE、REVERSAL 或 ADJUSTMENT")
        private String direction;

        /** 目标余额桶：PENDING 或 AVAILABLE；可为空。 */
        @Pattern(regexp = "PENDING|AVAILABLE", message = "目标余额桶只能为 PENDING 或 AVAILABLE")
        private String targetBucket;
    }
}

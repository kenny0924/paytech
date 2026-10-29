package com.paytech.risk.decision.api.control.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import lombok.Getter;
import lombok.Setter;

/**
 * 承保输入快照
 * <li>由 operations-console-bff 从 compliance、client-master 与 Platform 读取后组装；页面不能直接提交给 risk-decision。</li>
 * <li>草稿可缺省来源坐标与 MCC；提交与自动承保时由 risk-decision 校验"提交必填"并冻结。</li>
 */
@Getter
@Setter
public class UnderwritingInputBody {

    /** 快照时 Client 的法定名称或个人姓名，仅供列表展示。 */
    @NotBlank
    @Size(max = 255)
    private String clientDisplayName;

    /** 快照时 Client 编号。 */
    @NotBlank
    @Size(max = 64)
    private String clientNo;

    /** 快照时 Client 类型：INDIVIDUAL 或 ORGANIZATION。 */
    @NotBlank
    @Pattern(regexp = "INDIVIDUAL|ORGANIZATION")
    private String clientType;

    /**
     * 快照时 Client 入驻辖区代码
     * <li>取值来源 client-master 辖区包，非固定枚举。</li>
     */
    @NotBlank
    @Pattern(regexp = "[A-Z0-9_*]{1,16}")
    private String jurisdictionCode;

    /**
     * 尽调客户风险评级：LOW、MEDIUM 或 HIGH
     * <li>为空表示存量尽调未评 CRR，按 MEDIUM 处理，不能自动承保。</li>
     */
    @Pattern(regexp = "LOW|MEDIUM|HIGH")
    private String crr;

    /** 尽调档次：SIMPLIFIED、STANDARD 或 ENHANCED；可空。 */
    @Pattern(regexp = "SIMPLIFIED|STANDARD|ENHANCED")
    private String ddTier;

    /**
     * 运营确认的主 MCC，四位数字
     * <li>草稿可空，提交必填。</li>
     */
    @Pattern(regexp = "[0-9]{4}")
    private String mccCode;

    /** 运营确认的主行业代码；可空。 */
    @Size(max = 64)
    private String industryCode;

    /** 该 MCC 是否被卡组织公布为高风险（Platform MCC 参考数据）。 */
    @NotNull
    private Boolean networkHighRiskMcc;

    /**
     * 预计月交易金额，币种主单位十进制字符串
     * <li>≥ 0，最多 16 位整数、8 位小数；有值时 volumeCurrency 必填。</li>
     */
    @Pattern(regexp = "(0|[1-9][0-9]{0,15})(\\.[0-9]{1,8})?")
    private String expectedMonthlyVolume;

    /** 预计月交易金额与客单价的 ISO 4217 币种。 */
    @Pattern(regexp = "[A-Z]{3}")
    private String volumeCurrency;

    /** 预计单笔金额，格式同 expectedMonthlyVolume；可空。 */
    @Pattern(regexp = "(0|[1-9][0-9]{0,15})(\\.[0-9]{1,8})?")
    private String expectedTicket;

    /** 交付方式：INSTANT、PRE_ORDER、SUBSCRIPTION 或 SERVICE；存量申请未采集为空。 */
    @Pattern(regexp = "INSTANT|PRE_ORDER|SUBSCRIPTION|SERVICE")
    private String fulfillmentType;

    /** 预售交付天数 1—365；仅 PRE_ORDER 可填。 */
    @Min(1)
    @Max(365)
    private Integer fulfillmentDays;

    /** 申报的网站。 */
    @Size(max = 2048)
    private String websiteUrl;

    /** 申报的退款政策链接。 */
    @Size(max = 2048)
    private String refundPolicyUrl;

    /** 期望支付方式，client-master 规范化逗号串，原样冻结。 */
    @Size(max = 512)
    private String expectedPaymentMethods;

    /** 来源 compliance 入驻尽调案件 ID，十进制字符串；提交必填。 */
    @Pattern(regexp = "[1-9][0-9]{0,18}")
    private String sourceCaseId;

    /** 读取时的尽调案件版本，十进制字符串；提交必填。 */
    @Pattern(regexp = "(0|[1-9][0-9]{0,18})")
    private String sourceCaseVersion;

    /** 来源 client-master 入驻申请 ID，十进制字符串；提交必填。 */
    @Pattern(regexp = "[1-9][0-9]{0,18}")
    private String sourceApplicationId;

    /** 读取时的申请资料版本 draftRevision，十进制字符串；提交必填。 */
    @Pattern(regexp = "(0|[1-9][0-9]{0,18})")
    private String sourceDraftRevision;

    /**
     * 运营确认 MCC / 行业分类的时间，UTC ISO-8601（最多 6 位小数）
     * <li>为空表示分类未确认，不能提交。</li>
     */
    @Pattern(regexp = "\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(\\.\\d{1,6})?Z")
    private String sourceClassificationConfirmedAt;
}

package com.paytech.tax.api.request;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import lombok.Getter;
import lombok.Setter;

/**
 * 税务计算请求
 * <li>金额为币种主单位的普通十进制字符串，精度不得超过币种 minor unit（不隐式舍入）；ID 为正十进制字符串。</li>
 * <li>计算身份：sourceSystem + sourceObjectType + sourceObjectId + sourceRevision + taxScopeKey。</li>
 */
@Getter
@Setter
public class CalculateTaxRequestBody {
    /**
     * 来源系统
     * <li>本期为 acquiring。</li>
     */
    @NotBlank
    @Pattern(regexp = "[a-z][a-z0-9-]{0,31}", message = "必须是小写字母开头的小写字母、数字或连字符，最长 32 位")
    private String sourceSystem;

    /**
     * 来源对象类型
     * <li>本期只支持 ORDER。</li>
     */
    @NotBlank
    @Pattern(regexp = "ORDER", message = "本期只支持 ORDER")
    private String sourceObjectType;

    /** 来源对象 ID（acquiring 订单 ID），正十进制字符串 */
    @NotBlank
    @Pattern(regexp = "[1-9][0-9]{0,18}", message = "必须是正十进制整数字符串")
    private String sourceObjectId;

    /** 来源对象公开编号（acquiring 订单号），供运营按订单号查询 */
    @NotBlank
    @Pattern(regexp = "[A-Za-z0-9][A-Za-z0-9._-]{0,63}", message = "必须是 1–64 位字母、数字、点、下划线或连字符")
    private String sourceObjectNo;

    /**
     * 来源准备版本
     * <li>同一来源对象每次报价递增；建单为 1，消费者资料提交后的重算为 2、3……</li>
     */
    @NotNull
    @Min(1)
    @Max(1_000_000)
    private Integer sourceRevision;

    /**
     * 税务范围键
     * <li>本期只支持 SALE。</li>
     */
    @NotBlank
    @Pattern(regexp = "SALE", message = "本期只支持 SALE")
    private String taxScopeKey;

    /** 计算阶段（TaxCalculationStage）：PROVISIONAL、FINAL */
    @NotBlank
    @Pattern(regexp = "PROVISIONAL|FINAL", message = "只能为 PROVISIONAL 或 FINAL")
    private String stage;

    /**
     * 供应类型
     * <li>本期只支持 SALE（消费者购买）。</li>
     */
    @NotBlank
    @Pattern(regexp = "SALE", message = "本期只支持 SALE")
    private String supplyKind;

    /**
     * 商业模式代码（commercial BusinessMode，例如 MOR、PSP）
     * <li>只冻结为证据，不直接决定税负；是否计税由 taxSubjectPartyId 是否为平台登记纳税主体决定。</li>
     */
    @NotBlank
    @Pattern(regexp = "[A-Z][A-Z0-9_]{0,31}", message = "必须是大写字母开头的大写字母、数字或下划线，最长 32 位")
    private String businessMode;

    /**
     * 卖方法律主体 Party ID
     * <li>可空；MoR 时通常等于 taxSubjectPartyId。</li>
     */
    @Pattern(regexp = "[1-9][0-9]{0,18}", message = "必须是正十进制整数字符串")
    private String sellerPartyId;

    /**
     * 税务责任方 Party ID（商业关系快照 TAX_RESPONSIBLE 角色的 partyId）
     * <li>可空：调用方无法确定责任方时（CLIENT 来源 OE 没有商业关系快照，责任方在 Client 一侧）不传，结果恒为 NOT_MANAGED，
     * notManagedReason = TAX_RESPONSIBLE_UNRESOLVED。</li>
     */
    @Pattern(regexp = "[1-9][0-9]{0,18}", message = "必须是正十进制整数字符串")
    private String taxSubjectPartyId;

    /** 买方所属 Client ID */
    @NotBlank
    @Pattern(regexp = "[1-9][0-9]{0,18}", message = "必须是正十进制整数字符串")
    private String buyerClientId;

    /** 收单 Operating Entity 编号 */
    @NotBlank
    @Pattern(regexp = "[A-Z]{2}[0-9]{3}", message = "必须是 2 位大写字母加 3 位数字")
    private String entityNo;

    /** ISO 4217 币种 */
    @NotBlank
    @Pattern(regexp = "[A-Z]{3}", message = "必须是 3 位大写字母")
    private String currency;

    /**
     * 行价格口径（TaxPriceMode）：EXCLUSIVE、INCLUSIVE
     * <li>本期 acquiring 恒 EXCLUSIVE；INCLUSIVE 只在接口级验收。</li>
     */
    @NotBlank
    @Pattern(regexp = "EXCLUSIVE|INCLUSIVE", message = "只能为 EXCLUSIVE 或 INCLUSIVE")
    private String priceMode;

    /**
     * 候选税点日期 yyyy-MM-dd
     * <li>由调用方按本地法定日期给出（本期取报价当日 UTC 日期）；用于选择有效的 market、地区包与税率。</li>
     */
    @NotBlank
    @Pattern(regexp = "[0-9]{4}-[0-9]{2}-[0-9]{2}", message = "必须是 yyyy-MM-dd")
    private String taxPointDate;

    /**
     * 国家证据
     * <li>PROVISIONAL 需要 DECLARED_COUNTRY，FINAL 需要 BILLING_COUNTRY；缺少时结果为 BLOCKED(MISSING_EVIDENCE)，不是 400。</li>
     * <li>同一类型至多一条。</li>
     */
    @NotNull
    @Size(max = 4)
    @Valid
    private List<TaxEvidenceRequestBody> evidence;

    /** 商品行，1–200 行；lineNo 与 lineRef 在请求内各自唯一 */
    @NotEmpty
    @Size(max = 200)
    @Valid
    private List<TaxCalculationLineRequestBody> lines;

    /**
     * 自报税额
     * <li>只在结果为 NOT_MANAGED 时使用：税额等于该值，缺省为 0；平台登记纳税主体的计算忽略该值。</li>
     */
    @Pattern(regexp = "(0|[1-9][0-9]{0,15})(\\.[0-9]{1,8})?", message = "必须是主货币单位的普通十进制字符串")
    private String declaredTaxAmount;
}

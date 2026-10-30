package com.paytech.tax.api.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import lombok.Getter;
import lombok.Setter;

/**
 * 计算请求的一条商品行
 * <li>金额按币种主单位，精度不超过币种 minor unit；lineAmount 必须等于 quantity × unitPrice − discountAmount。</li>
 */
@Getter
@Setter
public class TaxCalculationLineRequestBody {
    /** 源商品行引用（acquiring clientItemId），请求内唯一，原样返回 */
    @NotBlank
    @Size(max = 64)
    private String lineRef;

    /** 行号，从 1 开始，请求内唯一 */
    @NotNull
    @Min(1)
    @Max(200)
    private Integer lineNo;

    /**
     * 税类代码（订单行 tax_category_code 原值，例如 SAAS_AUTOMATED）
     * <li>Client 自由填写，只校验非空与长度 ≤ 64；不在 t_tax_category 或已停用的税类得到 BLOCKED(UNKNOWN_TAX_CATEGORY)，
     * 不返回 400、不默认（technique S-13）。</li>
     */
    @NotBlank
    @Size(max = 64)
    private String categoryCode;

    /**
     * 非敏感商品说明
     * <li>可空，最长 255；只供运营查询展示，不得含消费者资料。</li>
     */
    @Size(max = 255)
    private String description;

    /** 数量，正十进制字符串，最多 8 位小数 */
    @NotBlank
    @Pattern(regexp = "(0|[1-9][0-9]{0,15})(\\.[0-9]{1,8})?", message = "必须是普通十进制字符串")
    private String quantity;

    /** 单价，按 priceMode 口径 */
    @NotBlank
    @Pattern(regexp = "(0|[1-9][0-9]{0,15})(\\.[0-9]{1,8})?", message = "必须是主货币单位的普通十进制字符串")
    private String unitPrice;

    /** 整行折扣（不乘数量），与价格同一口径；无折扣为 0 */
    @NotBlank
    @Pattern(regexp = "(0|[1-9][0-9]{0,15})(\\.[0-9]{1,8})?", message = "必须是主货币单位的普通十进制字符串")
    private String discountAmount;

    /**
     * 行金额 = quantity × unitPrice − discountAmount
     * <li>EXCLUSIVE 时为未税净额，INCLUSIVE 时为含税金额。</li>
     */
    @NotBlank
    @Pattern(regexp = "(0|[1-9][0-9]{0,15})(\\.[0-9]{1,8})?", message = "必须是主货币单位的普通十进制字符串")
    private String lineAmount;
}

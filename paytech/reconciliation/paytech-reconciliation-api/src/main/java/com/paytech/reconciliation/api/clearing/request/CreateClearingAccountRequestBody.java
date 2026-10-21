package com.paytech.reconciliation.api.clearing.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import lombok.Getter;
import lombok.Setter;

/** 新建清算账户请求。 */
@Getter
@Setter
public class CreateClearingAccountRequestBody {
    /**
     * 清算账户编码
     * <li>全局唯一，渠道账单文件头用它指明所属清算账户；创建后不可改。</li>
     */
    @NotBlank
    @Pattern(regexp = "[A-Z][A-Z0-9_-]{1,63}", message = "必须是大写字母开头的大写字母、数字、下划线或连字符，2–64 位")
    private String clearingAccountCode;

    /**
     * 渠道编码
     * <li>channel-connector 渠道目录中的编码；挂入的 MID 必须属于该渠道。</li>
     */
    @NotBlank
    @Pattern(regexp = "[A-Z][A-Z0-9_]{1,63}", message = "必须是大写字母开头的大写字母、数字或下划线，2–64 位")
    private String channelCode;

    /**
     * 与渠道签约的经营主体编号
     * <li>挂入的 MID 必须属于该主体。</li>
     */
    @NotBlank
    @Pattern(regexp = "[A-Z]{2}[0-9]{3}", message = "必须是两位大写字母加三位数字")
    private String entityNo;

    /**
     * 清算币种
     * <li>大写 ISO 4217；渠道账单币种必须与之相同。</li>
     */
    @NotBlank
    @Pattern(regexp = "[A-Z]{3}", message = "必须是大写 ISO 4217 三位代码")
    private String currency;

    /**
     * 到账银行账户主键
     * <li>可为空；非空时必须是同主体、同币种、用途 CHANNEL_SETTLEMENT 的 ACTIVE 银行账户。为空时账单不能对平。</li>
     */
    @Pattern(regexp = "[1-9][0-9]{0,18}", message = "必须是正十进制整数字符串")
    private String settlementCashAccountId;

    /**
     * 账单行未命中平台流水的等待天数
     * <li>期满转为 CHANNEL_LINE_UNMATCHED 异常；0–365。</li>
     */
    @NotNull
    @Min(0)
    @Max(365)
    private Integer unmatchedWaitDays;

    /**
     * 单笔平台流水实际渠道费与预估之差的容差
     * <li>主货币单位非负十进制字符串，不超过币种精度；超过开 COST_DEVIATION 异常。</li>
     */
    @NotBlank
    @Pattern(regexp = "(0|[1-9][0-9]{0,15})(\\.[0-9]{1,8})?", message = "必须是主货币单位的非负普通十进制字符串")
    private String costToleranceAmount;

    /**
     * 预期银行流水到账宽限天数
     * <li>超过预期日该天数仍未到账视为逾期（超期检测在切片 2 使用）；0–365。</li>
     */
    @NotNull
    @Min(0)
    @Max(365)
    private Integer cashGraceDays;
}

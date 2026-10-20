package com.paytech.ledger.api.request;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import lombok.Getter;
import lombok.Setter;

/** 创建并提交人工会计调整请求。 */
@Getter
@Setter
public class SubmitManualAdjustmentRequestBody {
    /**
     * 调整账簿主键
     */
    @NotBlank
    @Pattern(regexp = "[1-9][0-9]{0,18}", message = "必须是正十进制整数字符串")
    private String ledgerBookId;

    /**
     * 调整会计期间主键
     * <li>必须 OPEN 且属于账簿主体</li>
     */
    @NotBlank
    @Pattern(regexp = "[1-9][0-9]{0,18}", message = "必须是正十进制整数字符串")
    private String accountingPeriodId;

    /**
     * 调整币种
     * <li>必须等于账簿币种</li>
     */
    @NotBlank
    @Pattern(regexp = "[A-Z]{3}", message = "必须是大写 ISO 4217 三位代码")
    private String currency;

    /**
     * 调整原因代码
     * <li>Finance 人工调整原因目录</li>
     */
    @NotBlank
    @Size(max = 64)
    private String reasonCode;

    /**
     * Platform 证据文件主键
     * <li>可为空；与 fileContentHash 成对提供，本期不向 Platform 校验</li>
     */
    @Pattern(regexp = "[1-9][0-9]{0,18}", message = "必须是正十进制整数字符串")
    private String fileId;

    /**
     * 证据文件内容摘要
     * <li>64 位小写十六进制，与 fileId 成对提供</li>
     */
    @Pattern(regexp = "[0-9a-f]{64}", message = "必须是 64 位小写十六进制")
    private String fileContentHash;


    /**
     * 调整明细
     * <li>至少两行，借贷合计相等</li>
     */
    @NotNull
    @Size(min = 2, max = 500)
    private List<@Valid Line> lines;

    /** 人工调整明细。 */
    @Getter
    @Setter
    public static class Line {
        /**
         * 记账账户主键
         * <li>必须属于调整账簿且为 ACTIVE。</li>
         */
        @NotBlank
        @Pattern(regexp = "[1-9][0-9]{0,18}", message = "必须是正十进制整数字符串")
        private String accountId;

        /**
         * 借贷方向
         * <li>DEBIT 或 CREDIT。</li>
         */
        @NotBlank
        @Pattern(regexp = "DEBIT|CREDIT", message = "只能为 DEBIT 或 CREDIT")
        private String entrySide;

        /**
         * 金额
         * <li>主货币单位十进制字符串，大于零且不超过币种精度，例如 "100.00"。</li>
         */
        @NotBlank
        @Pattern(regexp = "(0|[1-9][0-9]{0,15})(\\.[0-9]{1,8})?", message = "必须是主货币单位的普通十进制字符串")
        private String amount;

        /**
         * 不含敏感值的业务说明
         * <li>最长 512 位。</li>
         */
        @NotBlank
        @Size(max = 512)
        private String lineDescription;
    }
}

package com.paytech.reconciliation.api.statement.request;

import java.time.LocalDate;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import lombok.Getter;
import lombok.Setter;

/**
 * 开始导入渠道账单请求：标准账单 CSV 的账单头（#HEADER 行）加文件信息
 * <li>恒等式在完成导入时校验：grossAmount = Σ CREDIT 行 amount − Σ DEBIT 行 amount（不含 FEE 行）；feeAmount = Σ 明细 feeAmount +
 * Σ FEE 行 amount；netAmount 带 cashDirection 等于 grossAmount − feeAmount。</li>
 */
@Getter
@Setter
public class StartChannelStatementImportRequestBody {
    /**
     * 清算账户编码
     * <li>必须存在且 ACTIVE。</li>
     */
    @NotBlank
    @Pattern(regexp = "[A-Z][A-Z0-9_-]{1,63}", message = "必须是大写字母开头的大写字母、数字、下划线或连字符，2–64 位")
    private String clearingAccountCode;

    /**
     * 渠道结算批次号
     * <li>可见 ASCII，最长 128；同一清算账户 + 批次号只允许一个未作废版本。</li>
     */
    @NotBlank
    @Pattern(regexp = "[\\x21-\\x7E]{1,128}", message = "必须是 1–128 位可见 ASCII 字符")
    private String channelBatchRef;

    /**
     * 渠道结算日
     * <li>YYYY-MM-DD。</li>
     */
    @NotNull
    private LocalDate settlementDate;

    /**
     * 账单币种
     * <li>必须等于清算账户币种。</li>
     */
    @NotBlank
    @Pattern(regexp = "[A-Z]{3}", message = "必须是大写 ISO 4217 三位代码")
    private String currency;

    /**
     * 账单头总额
     * <li>带符号主货币单位十进制字符串：贷记减借记，不含 FEE 行。</li>
     */
    @NotBlank
    @Pattern(regexp = "-?(0|[1-9][0-9]{0,15})(\\.[0-9]{1,8})?", message = "必须是主货币单位的带符号普通十进制字符串")
    private String grossAmount;

    /**
     * 账单头费用合计
     * <li>非负主货币单位十进制字符串。</li>
     */
    @NotBlank
    @Pattern(regexp = "(0|[1-9][0-9]{0,15})(\\.[0-9]{1,8})?", message = "必须是主货币单位的非负普通十进制字符串")
    private String feeAmount;

    /**
     * 净额绝对值
     * <li>非负主货币单位十进制字符串，方向见 cashDirection；cashDirection 为 NONE 时必须为 0。</li>
     */
    @NotBlank
    @Pattern(regexp = "(0|[1-9][0-9]{0,15})(\\.[0-9]{1,8})?", message = "必须是主货币单位的非负普通十进制字符串")
    private String netAmount;

    /**
     * 净额相对平台的方向
     * <li>CREDIT、DEBIT 或 NONE；切片 1 净额为借方（DEBIT）的账单在完成导入时拒绝。</li>
     */
    @NotBlank
    @Pattern(regexp = "CREDIT|DEBIT|NONE", message = "只能为 CREDIT、DEBIT 或 NONE")
    private String cashDirection;

    /**
     * 运营上传的原文件名
     * <li>仅用于展示，最长 255。</li>
     */
    @NotBlank
    @Size(max = 255)
    private String fileName;

    /**
     * 浏览器对整个原文件计算的 SHA-256
     * <li>64 位小写十六进制；服务不保存原文件。</li>
     */
    @NotBlank
    @Pattern(regexp = "[0-9a-f]{64}", message = "必须是 64 位小写十六进制")
    private String fileContentHash;

    /**
     * 声明的明细行数
     * <li>1–20,000；完成导入时必须等于已接收行数，行号 1..N 连续。</li>
     */
    @NotNull
    @Min(1)
    @Max(20000)
    private Integer declaredLineCount;
}

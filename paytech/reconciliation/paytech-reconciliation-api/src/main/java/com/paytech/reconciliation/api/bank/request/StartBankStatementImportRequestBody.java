package com.paytech.reconciliation.api.bank.request;

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
 * 开始导入银行对账单请求：页面先选的银行账户、标准对账单 CSV 的头部与文件信息
 * <li>恒等式在完成导入时校验：openingBalance + Σ CREDIT 行 amount − Σ DEBIT 行 amount = closingBalance。</li>
 */
@Getter
@Setter
public class StartBankStatementImportRequestBody {
    /**
     * 银行账户主键
     * <li>页面先选；必须 ACTIVE。</li>
     */
    @NotBlank
    @Pattern(regexp = "[1-9][0-9]{0,18}", message = "必须是正十进制整数字符串")
    private String cashAccountId;

    /**
     * 文件头的银行账号末四位
     * <li>必须等于所选银行账户的末四位，否则拒绝。</li>
     */
    @NotBlank
    @Pattern(regexp = "[A-Za-z0-9]{4}", message = "必须是 4 位字母或数字")
    private String bankAccountLast4;

    /**
     * 银行对账单编号
     * <li>可见 ASCII，最长 128；同一银行账户唯一。</li>
     */
    @NotBlank
    @Pattern(regexp = "[\\x21-\\x7E]{1,128}", message = "必须是 1–128 位可见 ASCII 字符")
    private String statementRef;

    /**
     * 期间开始日（含）
     */
    @NotNull
    private LocalDate periodStart;

    /**
     * 期间结束日（含）
     * <li>不早于 periodStart。</li>
     */
    @NotNull
    private LocalDate periodEnd;

    /**
     * 币种
     * <li>必须等于银行账户币种。</li>
     */
    @NotBlank
    @Pattern(regexp = "[A-Z]{3}", message = "必须是大写 ISO 4217 三位代码")
    private String currency;

    /**
     * 银行报告的期初余额
     * <li>带符号主货币单位十进制字符串。</li>
     */
    @NotBlank
    @Pattern(regexp = "-?(0|[1-9][0-9]{0,15})(\\.[0-9]{1,8})?", message = "必须是主货币单位的带符号普通十进制字符串")
    private String openingBalance;

    /**
     * 银行报告的期末余额
     * <li>带符号主货币单位十进制字符串。</li>
     */
    @NotBlank
    @Pattern(regexp = "-?(0|[1-9][0-9]{0,15})(\\.[0-9]{1,8})?", message = "必须是主货币单位的带符号普通十进制字符串")
    private String closingBalance;

    /**
     * 原文件名
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
     * 声明的流水行数
     * <li>0–20,000（期间无流水的对账单为 0，开始后直接完成）；完成导入时必须等于已接收行数，行号 1..N 连续。</li>
     */
    @NotNull
    @Min(0)
    @Max(20000)
    private Integer declaredLineCount;
}

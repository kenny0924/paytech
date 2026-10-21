package com.paytech.reconciliation.api.bank.request;

import java.time.LocalDate;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import lombok.Getter;
import lombok.Setter;

/** 追加一段银行流水行请求：标准银行对账单 CSV 的明细行，每段 1–100 行。 */
@Getter
@Setter
public class AppendBankStatementLinesRequestBody {
    /**
     * 本段流水行
     * <li>1–100 行；同一段内行号不得重复。</li>
     */
    @NotNull
    @Size(min = 1, max = 100)
    private List<@Valid @NotNull Line> lines;

    /** 银行流水行（CSV 一行）。 */
    @Getter
    @Setter
    public static class Line {
        /**
         * 文件内行号
         * <li>从 1 起，不超过声明行数；幂等键：同号同内容忽略，同号不同内容拒绝。</li>
         */
        @NotNull
        @Min(1)
        @Max(20000)
        private Integer lineNo;

        /**
         * 银行交易号
         * <li>可见 ASCII，最长 128；同一银行账户已入库的交易号在完成导入时跳过并计数。</li>
         */
        @NotBlank
        @Pattern(regexp = "[\\x21-\\x7E]{1,128}", message = "必须是 1–128 位可见 ASCII 字符")
        private String bankTransactionRef;

        /**
         * 起息日
         * <li>YYYY-MM-DD，应在对账单期间内。</li>
         */
        @NotNull
        private LocalDate valueDate;

        /**
         * 相对平台账户的方向
         * <li>CREDIT 或 DEBIT。</li>
         */
        @NotBlank
        @Pattern(regexp = "CREDIT|DEBIT", message = "只能为 CREDIT 或 DEBIT")
        private String direction;

        /**
         * 流水金额
         * <li>正数主货币单位十进制字符串。</li>
         */
        @NotBlank
        @Pattern(regexp = "(0|[1-9][0-9]{0,15})(\\.[0-9]{1,8})?", message = "必须是主货币单位的非负普通十进制字符串")
        private String amount;

        /**
         * 银行给出的对方名称
         * <li>可为空，最长 255。</li>
         */
        @Size(max = 255)
        private String counterpartyName;

        /**
         * 附言或参考号
         * <li>可为空，最长 500；仅用于人工识别。</li>
         */
        @Size(max = 500)
        private String referenceText;
    }
}

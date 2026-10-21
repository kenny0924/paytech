package com.paytech.reconciliation.api.statement.request;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import lombok.Getter;
import lombok.Setter;

/** 追加一段渠道账单明细行请求：标准账单 CSV 的明细行，每段 1–100 行。 */
@Getter
@Setter
public class AppendChannelStatementLinesRequestBody {
    /**
     * 本段明细行
     * <li>1–100 行；同一段内行号不得重复。</li>
     */
    @NotNull
    @Size(min = 1, max = 100)
    private List<@Valid @NotNull Line> lines;

    /** 渠道账单明细行（CSV 一行）。 */
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
         * 行类型
         * <li>PAYMENT、REFUND、CHARGEBACK、FEE 或 ADJUSTMENT。</li>
         */
        @NotBlank
        @Pattern(regexp = "PAYMENT|REFUND|CHARGEBACK|FEE|ADJUSTMENT", message = "只能为 PAYMENT、REFUND、CHARGEBACK、FEE 或 ADJUSTMENT")
        private String lineType;

        /**
         * 相对平台的资金方向
         * <li>CREDIT 或 DEBIT；ADJUSTMENT 行必须显式声明。</li>
         */
        @NotBlank
        @Pattern(regexp = "CREDIT|DEBIT", message = "只能为 CREDIT 或 DEBIT")
        private String direction;

        /**
         * 行金额
         * <li>非负主货币单位十进制字符串。</li>
         */
        @NotBlank
        @Pattern(regexp = "(0|[1-9][0-9]{0,15})(\\.[0-9]{1,8})?", message = "必须是主货币单位的非负普通十进制字符串")
        private String amount;

        /**
         * 行上直接扣的渠道费
         * <li>非负主货币单位十进制字符串；没有费用填 0。</li>
         */
        @NotBlank
        @Pattern(regexp = "(0|[1-9][0-9]{0,15})(\\.[0-9]{1,8})?", message = "必须是主货币单位的非负普通十进制字符串")
        private String feeAmount;

        /**
         * 外部交易号类型
         * <li>Payment 渠道交易引用类型目录中的 Code，非固定枚举；ADJUSTMENT 行与不带交易号的 FEE 行为空，其余行必填（完成导入时校验）；
         * 与 externalTransactionId 同有同无。</li>
         */
        @Pattern(regexp = "[A-Z][A-Z0-9_]{0,31}", message = "必须是大写字母开头的大写字母、数字或下划线，最长 32 位")
        private String referenceType;

        /**
         * 渠道外部交易号
         * <li>可见 ASCII，最长 128；为空规则同 referenceType。</li>
         */
        @Pattern(regexp = "[\\x21-\\x7E]{1,128}", message = "必须是 1–128 位可见 ASCII 字符")
        private String externalTransactionId;

        /**
         * 渠道记录的交易日期
         * <li>YYYY-MM-DD；可为空。</li>
         */
        private LocalDate transactionDate;

        /**
         * 标准列以外的渠道特有列
         * <li>列名到原值的映射，原样保存为 JSON Object，序列化后最大 4 KiB；不参与匹配。可为空。</li>
         */
        @Size(max = 64)
        private Map<@NotBlank @Size(max = 64) String, @NotNull @Size(max = 1024) String> rawAttributes;
    }
}

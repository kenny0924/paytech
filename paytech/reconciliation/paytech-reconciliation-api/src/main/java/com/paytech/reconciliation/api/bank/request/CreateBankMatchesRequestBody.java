package com.paytech.reconciliation.api.bank.request;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import lombok.Getter;
import lombok.Setter;

/**
 * 人工多选匹配请求：一条银行流水分配给多条预期银行流水
 * <li>每条分配对流水的带符号方向由预期流水方向与流水方向是否相同决定（同向为正）；带符号合计加已确认合计不超过流水金额。</li>
 */
@Getter
@Setter
public class CreateBankMatchesRequestBody {
    /**
     * 银行流水主键
     */
    @NotBlank
    @Pattern(regexp = "[1-9][0-9]{0,18}", message = "必须是正十进制整数字符串")
    private String bankStatementLineId;

    /**
     * 分配
     * <li>1–50 条；同一预期流水只能出现一次，必须与流水同一银行账户。</li>
     */
    @NotNull
    @Size(min = 1, max = 50)
    private List<@Valid @NotNull Allocation> allocations;

    /** 对一条预期银行流水的分配。 */
    @Getter
    @Setter
    public static class Allocation {
        /**
         * 预期银行流水主键
         */
        @NotBlank
        @Pattern(regexp = "[1-9][0-9]{0,18}", message = "必须是正十进制整数字符串")
        private String expectedMovementId;

        /**
         * 分配金额
         * <li>正数主货币单位十进制字符串，不超过预期流水剩余金额。</li>
         */
        @NotBlank
        @Pattern(regexp = "(0|[1-9][0-9]{0,15})(\\.[0-9]{1,8})?", message = "必须是主货币单位的非负普通十进制字符串")
        private String amount;
    }
}

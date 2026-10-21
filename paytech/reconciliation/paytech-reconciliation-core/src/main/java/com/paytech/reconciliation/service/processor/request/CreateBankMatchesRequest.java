package com.paytech.reconciliation.service.processor.request;

import java.math.BigDecimal;
import java.util.List;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** 人工多选匹配命令：一条银行流水分配给多条预期银行流水。 */
@Getter
@AllArgsConstructor
public class CreateBankMatchesRequest {
    /** 操作人 Operations Account ID */
    @NotNull
    private final Long actorId;

    /** 银行流水主键 */
    @NotNull
    private final Long bankStatementLineId;

    /** 分配，1–50 条 */
    @NotNull
    @Size(min = 1, max = 50)
    private final List<@Valid @NotNull Allocation> allocations;

    /** 对一条预期银行流水的分配。 */
    @Getter
    @AllArgsConstructor
    public static class Allocation {
        /** 预期银行流水主键 */
        @NotNull
        private final Long expectedMovementId;

        /** 分配金额 */
        @NotNull
        private final BigDecimal amount;
    }
}

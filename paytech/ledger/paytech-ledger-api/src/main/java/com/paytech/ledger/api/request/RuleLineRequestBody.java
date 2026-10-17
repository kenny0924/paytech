package com.paytech.ledger.api.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import lombok.Getter;
import lombok.Setter;

/** 自动记账规则行。 */
@Getter
@Setter
public class RuleLineRequestBody {
    /**
     * 指令类型
     * <li>PAYMENT_SUCCEEDED、PLATFORM_SERVICE_CHARGE_PARTNER、PLATFORM_SERVICE_CHARGE_PLATFORM；目标账簿由类型固定决定。</li>
     */
    @NotBlank
    @Size(max = 64)
    private String instructionType;

    /**
     * 同一指令类型内的行号
     * <li>正整数，决定指令行顺序。</li>
     */
    @NotNull
    private Integer lineNo;

    /**
     * 同一指令类型内识别会计含义的行代码
     */
    @NotBlank
    @Size(max = 64)
    private String lineCode;

    /**
     * 金额来源
     * <li>GROSS_AMOUNT 取交易全额，其余按同名交易经济组件汇总。</li>
     */
    @NotBlank
    @Size(max = 64)
    private String amountSourceCode;

    /**
     * 核算对象角色
     * <li>由固定解释器解析为账户 Owner 类型与标识。</li>
     */
    @NotBlank
    @Size(max = 64)
    private String ownerRole;

    /**
     * 科目代码
     * <li>必须在 STANDARD 科目表中存在且允许过账。</li>
     */
    @NotBlank
    @Size(max = 64)
    private String accountCode;

    /**
     * 余额桶
     * <li>PENDING、AVAILABLE、LOCKED 仅用于 Client/Partner 应付科目，其余为 NONE。</li>
     */
    @NotBlank
    @Size(max = 64)
    private String balanceBucket;

    /**
     * 借贷方向
     * <li>DEBIT 或 CREDIT。</li>
     */
    @NotBlank
    @Size(max = 64)
    private String entrySide;

    /**
     * 金额来源缺失时是否拒绝生成指令
     * <li>来源存在但金额为零时不视为缺失，同来源规则行整体跳过。</li>
     */
    @NotNull
    private Boolean requiredFlag;
}

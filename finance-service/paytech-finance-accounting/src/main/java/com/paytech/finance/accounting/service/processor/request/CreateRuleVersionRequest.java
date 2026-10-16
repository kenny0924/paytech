package com.paytech.finance.accounting.service.processor.request;

import java.time.Instant;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import lombok.Getter;
import lombok.Setter;

/** 创建自动记账规则版本；规则行代码保持 Wire 字符串，由 Processor 按目录解析并以业务错误拒绝非法值。 */
@Getter
@Setter
public class CreateRuleVersionRequest {
    /** 规则集代码 */
    @NotBlank
    private String bundleCode;

    /** 编制人 */
    @NotNull
    private Long makerId;

    /** 开始适用时间 */
    @NotNull
    private Instant effectiveFrom;

    /** 完整有序规则行集 */
    @NotNull
    private List<@Valid Line> lines;

    /** 规则行。 */
    @Getter
    @Setter
    public static class Line {
        /** 指令类型代码。 */
        @NotBlank
        private String instructionType;

        /** 同一指令类型内的行号。 */
        @NotNull
        private Integer lineNo;

        /** 同一指令类型内的行代码。 */
        @NotBlank
        private String lineCode;

        /** 金额来源代码。 */
        @NotBlank
        private String amountSourceCode;

        /** 核算对象角色代码。 */
        @NotBlank
        private String ownerRole;

        /** 科目代码。 */
        @NotBlank
        private String accountCode;

        /** 余额桶代码。 */
        @NotBlank
        private String balanceBucket;

        /** 借贷方向代码。 */
        @NotBlank
        private String entrySide;

        /** 金额来源缺失时是否拒绝生成指令。 */
        @NotNull
        private Boolean requiredFlag;
    }
}

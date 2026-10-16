package com.paytech.finance.api.accounting.response;

import java.time.Instant;
import java.util.List;

import lombok.Getter;
import lombok.Setter;

/** 自动记账规则版本详情。 */
@Getter
@Setter
public class RuleVersionResponseBody {
    /** 版本主键。 */
    private String versionId;

    /** 规则集代码。 */
    private String bundleCode;

    /** 规则集内递增的版本号。 */
    private String bundleVer;

    /** 开始适用时间（UTC）。 */
    private Instant effectiveFrom;

    /**
     * 停止适用时间（UTC）
     * <li>未失效时为空；被新版本替代时等于新版本开始时间。</li>
     */
    private Instant effectiveTo;

    /**
     * 版本状态
     * <li>DRAFT、PENDING_APPROVAL、APPROVED、ACTIVE、REJECTED、RETIRED。</li>
     */
    private String status;

    /** 编制人。 */
    private String makerId;

    /**
     * 复核人
     * <li>未复核时为空。</li>
     */
    private String checkerId;

    /**
     * 批准时间（UTC）
     * <li>未批准或已驳回时为空。</li>
     */
    private Instant approvedTime;

    /** 按指令类型、行号升序的规则行。 */
    private List<Line> lines;

    /** 规则行。 */
    @Getter
    @Setter
    public static class Line {
        /** 规则行主键。 */
        private String ruleLineId;

        /** 指令类型。 */
        private String instructionType;

        /** 同一指令类型内的行号。 */
        private Integer lineNo;

        /** 同一指令类型内的行代码。 */
        private String lineCode;

        /** 金额来源。 */
        private String amountSourceCode;

        /** 核算对象角色。 */
        private String ownerRole;

        /** 科目代码。 */
        private String accountCode;

        /** 余额桶。 */
        private String balanceBucket;

        /** 借贷方向。 */
        private String entrySide;

        /** 金额来源缺失时是否拒绝生成指令。 */
        private Boolean requiredFlag;
    }
}

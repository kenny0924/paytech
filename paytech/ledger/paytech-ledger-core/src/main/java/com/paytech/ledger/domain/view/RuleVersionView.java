package com.paytech.ledger.domain.view;

import java.time.Instant;
import java.util.List;

import com.paytech.account.api.enums.BalanceBucket;
import com.paytech.billing.api.enums.AmountSource;
import com.paytech.billing.api.enums.AutoInstructionType;
import com.paytech.ledger.api.enums.RuleOwnerRole;
import com.paytech.ledger.core.dal.dataobject.AccountingRuleBundleVer;
import com.paytech.ledger.core.dal.dataobject.AccountingRuleLine;
import com.paytech.ledger.core.enums.EntrySide;
import com.paytech.ledger.core.enums.RuleVersionStatus;

import lombok.Getter;
import lombok.Setter;

/** 规则版本详情：规则集代码、版本与规则行的组合结果。 */
@Getter
@Setter
public class RuleVersionView {
    /** 版本主键。 */
    private Long versionId;

    /** 规则集代码。 */
    private String bundleCode;

    /** 版本号。 */
    private Long bundleVer;

    /** 开始适用时间。 */
    private Instant effectiveFrom;

    /**
     * 停止适用时间
     * <li>未失效时为空。</li>
     */
    private Instant effectiveTo;

    /** 版本状态。 */
    private RuleVersionStatus status;

    /** 编制人。 */
    private Long makerId;

    /**
     * 复核人
     * <li>未复核时为空。</li>
     */
    private Long checkerId;

    /**
     * 批准时间
     * <li>未批准或已驳回时为空。</li>
     */
    private Instant approvedTime;

    /** 按指令类型、行号升序的规则行。 */
    private List<Line> lines;

    /** 由版本与规则行组装详情。 */
    public static RuleVersionView of(String bundleCode, AccountingRuleBundleVer ver, List<AccountingRuleLine> lines) {
        RuleVersionView view = new RuleVersionView();
        view.setVersionId(ver.getId());
        view.setBundleCode(bundleCode);
        view.setBundleVer(ver.getBundleVer());
        view.setEffectiveFrom(ver.getEffectiveFrom());
        view.setEffectiveTo(ver.getEffectiveTo());
        view.setStatus(ver.getStatus());
        view.setMakerId(ver.getMakerId());
        view.setCheckerId(ver.getCheckerId());
        view.setApprovedTime(ver.getApprovedTime());
        view.setLines(lines.stream().map(Line::of).toList());
        return view;
    }

    /** 规则行。 */
    @Getter
    @Setter
    public static class Line {
        /** 规则行主键。 */
        private Long ruleLineId;

        /** 指令类型。 */
        private AutoInstructionType instructionType;

        /** 行号。 */
        private Integer lineNo;

        /** 行代码。 */
        private String lineCode;

        /** 金额来源。 */
        private AmountSource amountSourceCode;

        /** 核算对象角色。 */
        private RuleOwnerRole ownerRole;

        /** 科目代码。 */
        private String accountCode;

        /** 余额桶。 */
        private BalanceBucket balanceBucket;

        /** 借贷方向。 */
        private EntrySide entrySide;

        /** 金额来源缺失时是否拒绝生成指令。 */
        private Boolean requiredFlag;

        /** 由规则行组装。 */
        static Line of(AccountingRuleLine source) {
            Line line = new Line();
            line.setRuleLineId(source.getId());
            line.setInstructionType(source.getInstructionType());
            line.setLineNo(source.getLineNo());
            line.setLineCode(source.getLineCode());
            line.setAmountSourceCode(source.getAmountSourceCode());
            line.setOwnerRole(source.getOwnerRole());
            line.setAccountCode(source.getAccountCode());
            line.setBalanceBucket(source.getBalanceBucket());
            line.setEntrySide(source.getEntrySide());
            line.setRequiredFlag(source.getRequiredFlag());
            return line;
        }
    }
}

package com.paytech.risk.control.domainservice.validator.underwriting;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import com.paytech.risk.control.core.enums.FulfillmentType;
import com.paytech.risk.control.domain.underwriting.UnderwritingInput;
import com.paytech.risk.control.domain.underwriting.UnderwritingTerms;
import com.paytech.risk.control.domain.underwriting.UnderwritingValidationException;
import com.paytech.risk.control.domain.underwriting.UnderwritingValidationException.Issue;

/**
 * 承保内容的字段组合与提交必填校验，由建草稿、保存、提交、自动承保与修订共用
 * <li>草稿只校验字段组合（金额与币种、预售天数、保证金联动、高风险登记理由）；提交另校验 B2 / B5 的必填项。</li>
 * <li>字段路径与请求 Body 一致（input.*、decision.*），失败一次报告全部问题；纯内存，不访问数据库。</li>
 */
public final class UnderwritingContentValidator {
    /** 纯校验工具。 */
    private UnderwritingContentValidator() { }

    /**
     * 校验草稿内容的字段组合
     *
     * @param input 输入快照
     * @param terms 结论字段
     */
    public static void validateDraft(UnderwritingInput input, UnderwritingTerms terms) {
        List<Issue> issues = new ArrayList<>();
        combination(input, terms, issues);
        throwIfAny(issues);
    }

    /**
     * 校验将要冻结的提交内容：字段组合加提交必填项
     *
     * @param input 输入快照
     * @param terms 结论字段
     */
    public static void validateSubmission(UnderwritingInput input, UnderwritingTerms terms) {
        List<Issue> issues = new ArrayList<>();
        combination(input, terms, issues);
        requiredInput(input, issues);
        requiredTerms(terms, issues);
        throwIfAny(issues);
    }

    /** 草稿与提交共用的字段组合规则。 */
    static void combination(UnderwritingInput input, UnderwritingTerms terms, List<Issue> issues) {
        boolean hasAmount = input.getExpectedMonthlyVolume() != null || input.getExpectedTicket() != null;
        if (hasAmount && input.getVolumeCurrency() == null) {
            issues.add(new Issue("input.volumeCurrency", "填写预计流水或客单价时币种必填"));
        }
        if (input.getFulfillmentDays() != null && input.getFulfillmentType() != FulfillmentType.PRE_ORDER) {
            issues.add(new Issue("input.fulfillmentDays", "仅交付方式为预售时填写交付天数"));
        }
        deposit(terms, issues);
        Boolean highRiskMcc = terms.getHighRiskMcc();
        boolean changed = highRiskMcc != null && highRiskMcc != input.isNetworkHighRiskMcc();
        if (changed && isBlank(terms.getHighRiskMccReason())) {
            issues.add(new Issue("decision.highRiskMccReason", "更改卡组织登记标记的默认值时须写理由"));
        }
    }

    /** 保证金三项联动：只有要求保证金时才可填金额、币种与阻塞收款，金额须大于 0。 */
    static void deposit(UnderwritingTerms terms, List<Issue> issues) {
        if (Boolean.TRUE.equals(terms.getRequireDeposit())) {
            if (terms.getDepositAmount() != null && terms.getDepositAmount().compareTo(BigDecimal.ZERO) <= 0) {
                issues.add(new Issue("decision.depositAmount", "保证金金额须大于 0"));
            }
            return;
        }
        if (terms.getDepositAmount() != null) {
            issues.add(new Issue("decision.depositAmount", "不要求保证金时金额须为空"));
        }
        if (terms.getDepositCurrency() != null) {
            issues.add(new Issue("decision.depositCurrency", "不要求保证金时币种须为空"));
        }
        if (Boolean.TRUE.equals(terms.getDepositBlocksCollection())) {
            issues.add(new Issue("decision.depositBlocksCollection", "只有要求保证金时才可选择到账前阻塞收款"));
        }
    }

    /** 提交必填的输入：MCC 与来源坐标，分类须已确认。 */
    static void requiredInput(UnderwritingInput input, List<Issue> issues) {
        required(input.getMccCode(), "input.mccCode", "提交时 MCC 必填", issues);
        required(input.getSourceCaseId(), "input.sourceCaseId", "提交时尽调案件来源必填", issues);
        required(input.getSourceCaseVersion(), "input.sourceCaseVersion", "提交时尽调案件版本必填", issues);
        required(input.getSourceApplicationId(), "input.sourceApplicationId", "提交时入驻申请来源必填", issues);
        required(input.getSourceDraftRevision(), "input.sourceDraftRevision", "提交时申请资料版本必填", issues);
        required(input.getSourceClassificationConfirmedTime(), "input.sourceClassificationConfirmedAt",
            "MCC 分类尚未确认，不能提交", issues);
    }

    /** 提交必填的结论：观察期、保证金三项与卡组织登记标记。 */
    static void requiredTerms(UnderwritingTerms terms, List<Issue> issues) {
        required(terms.getObservationDays(), "decision.observationDays", "提交时观察期天数必填", issues);
        required(terms.getObservationTransactions(), "decision.observationTransactions", "提交时观察期笔数必填", issues);
        required(terms.getRequireDeposit(), "decision.requireDeposit", "提交时须选择是否要求保证金", issues);
        required(terms.getDepositBlocksCollection(), "decision.depositBlocksCollection", "提交时须选择是否阻塞收款", issues);
        required(terms.getHighRiskMcc(), "decision.highRiskMcc", "提交时须确认卡组织登记标记", issues);
        if (Boolean.TRUE.equals(terms.getRequireDeposit())) {
            required(terms.getDepositAmount(), "decision.depositAmount", "要求保证金时金额必填", issues);
            required(terms.getDepositCurrency(), "decision.depositCurrency", "要求保证金时币种必填", issues);
        }
    }

    /** 缺值时记录问题。 */
    static void required(Object value, String field, String message, List<Issue> issues) {
        if (value == null) {
            issues.add(new Issue(field, message));
        }
    }

    /** 空白文本。 */
    static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    /** 有问题时抛出校验失败。 */
    static void throwIfAny(List<Issue> issues) {
        if (!issues.isEmpty()) {
            throw new UnderwritingValidationException(issues);
        }
    }
}

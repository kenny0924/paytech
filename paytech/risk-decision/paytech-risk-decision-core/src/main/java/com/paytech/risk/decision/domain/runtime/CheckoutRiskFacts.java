package com.paytech.risk.decision.domain.runtime;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.LongSupplier;

import com.paytech.risk.decision.core.dal.dataobject.CheckoutInputSnapshot;
import com.paytech.risk.decision.core.dal.dataobject.DecisionEvaluation;
import com.paytech.risk.decision.core.dal.dataobject.ExecutionRelease;
import com.paytech.risk.decision.domain.authoring.DecisionPointAuthoringDescriptor;
import com.paytech.risk.decision.domain.enums.VelocityEventCode;

/**
 * 由一次正式结账评估的输入、结果与时间构造需要保存的全部事实；纯内存构造
 * <li>稳定输入摘要覆盖订单 ID 与全部输入字段；结账没有受限字段，评估的输入快照摘要即稳定摘要。</li>
 * <li>只有成功评估写入累计事件：每次写结账评估事件，结论为审核或拒绝时再写拦截事件；来源为本次输入快照，主体为 Client 与运营主体。</li>
 * <li>评估行、Feature 结果、轨迹与累计事件由 {@link RuntimeEvaluationFacts} 构造。</li>
 */
public final class CheckoutRiskFacts {
    /** 稳定输入摘要合同。 */
    static final String STABLE_SCHEMA = "CODE-MOR:RISK:CHECKOUT_PREPARE_STABLE_INPUT:1";

    /** 累计事件来源类型，与来源表同名；来源 ID 为快照 ID。 */
    static final String SOURCE_TYPE = "CHECKOUT_INPUT_SNAPSHOT";

    /** 纯构造工具。 */
    private CheckoutRiskFacts() { }

    /** 正式评估的请求身份。 */
    public static String requestId(long orderId) {
        return "CHECKOUT_PREPARE:" + orderId;
    }

    /**
     * 订单 ID 与全部输入字段的摘要
     *
     * @param input 冻结输入
     * @return SHA-256 小写十六进制
     */
    public static String stableHash(CheckoutRiskInput input) {
        return RuntimeHashes.sha256(STABLE_SCHEMA, stableFields(input));
    }

    /**
     * 构造需要保存的事实
     *
     * @param input 冻结输入
     * @param release 执行发布
     * @param run 执行结果、时间与技术状态
     * @param ids 标识生成
     * @return 评估上下文
     */
    public static CheckoutRiskAssessmentContext build(CheckoutRiskInput input, ExecutionRelease release,
        RuntimeEvaluationFacts.Run run, LongSupplier ids) {
        long evaluationId = ids.getAsLong();
        String stableHash = stableHash(input);
        DecisionEvaluation evaluation = RuntimeEvaluationFacts.primaryEvaluation(evaluationId,
            requestId(input.orderId()), release, run, stableHash);
        long snapshotId = ids.getAsLong();
        RuntimeEvaluationFacts.VelocityFacts velocity = RuntimeEvaluationFacts.velocityFacts(SOURCE_TYPE, snapshotId,
            input, run, VelocityEventCode.CHECKOUT_ASSESSED, VelocityEventCode.CHECKOUT_REJECTED,
            DecisionPointAuthoringDescriptor.checkoutPrepare().velocitySubjects(), ids);
        return new CheckoutRiskAssessmentContext(evaluation, snapshot(snapshotId, evaluationId, input, stableHash, run),
            RuntimeEvaluationFacts.featureResults(evaluationId, run, ids),
            RuntimeEvaluationFacts.ruleTraces(evaluationId, run, ids),
            RuntimeEvaluationFacts.flowTraces(evaluationId, run, ids), velocity.events(), velocity.subjects());
    }

    /** 结账输入快照行。 */
    static CheckoutInputSnapshot snapshot(long snapshotId, long evaluationId, CheckoutRiskInput input,
        String stableHash, RuntimeEvaluationFacts.Run run) {
        CheckoutInputSnapshot row = new CheckoutInputSnapshot();
        row.setSnapshotId(snapshotId);
        row.setEvaluationId(evaluationId);
        row.setOrderId(input.orderId());
        row.setClientId(input.clientId());
        row.setEntityNo(input.entityNo());
        row.setEntitySourceType(input.entitySourceType());
        row.setLocalPartnerId(input.localPartnerId());
        row.setConsumerCountryCode(input.consumerCountryCode());
        row.setCurrency(input.currency());
        row.setMerchandiseAmount(input.merchandiseAmount());
        row.setDiscountAmount(input.discountAmount());
        row.setNetAmount(input.netAmount());
        row.setIndustryCode(input.industryCode());
        row.setMccCode(input.mccCode());
        row.setLineCount(input.lineCount());
        row.setStableInputHash(stableHash);
        row.setCreateTime(run.completedTime());
        return row;
    }

    /** 订单 ID 与全部输入字段；金额去尾零，没有 Local Partner 时为空。 */
    static Map<String, String> stableFields(CheckoutRiskInput input) {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("orderId", Long.toString(input.orderId()));
        values.put("clientId", Long.toString(input.clientId()));
        values.put("entityNo", input.entityNo());
        values.put("entitySourceType", input.entitySourceType());
        values.put("localPartnerId", input.localPartnerId() == null ? null : Long.toString(input.localPartnerId()));
        values.put("consumerCountryCode", input.consumerCountryCode());
        values.put("currency", input.currency());
        values.put("merchandiseAmount", CheckoutRiskInput.plain(input.merchandiseAmount()));
        values.put("discountAmount", CheckoutRiskInput.plain(input.discountAmount()));
        values.put("netAmount", CheckoutRiskInput.plain(input.netAmount()));
        values.put("industryCode", input.industryCode());
        values.put("mccCode", input.mccCode());
        values.put("lineCount", Integer.toString(input.lineCount()));
        return values;
    }
}

package com.paytech.risk.decision.service.evaluation;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import com.paytech.foundation.common.error.ConcurrentRequestException;
import com.paytech.foundation.common.error.ErrorException;
import com.paytech.risk.decision.core.dal.dataobject.CheckoutInputSnapshot;
import com.paytech.risk.decision.core.dal.dataobject.DecisionEvaluation;
import com.paytech.risk.decision.core.dal.dataobject.ExecutionRelease;
import com.paytech.risk.decision.domain.authoring.DecisionPointAuthoringDescriptor;
import com.paytech.risk.decision.domain.enums.TechnicalStatus;
import com.paytech.risk.decision.domain.runtime.CheckoutRiskAssessmentContext;
import com.paytech.risk.decision.domain.runtime.CheckoutRiskFacts;
import com.paytech.risk.decision.domain.runtime.CheckoutRiskInput;
import com.paytech.risk.decision.domain.runtime.EvaluationInputContract;
import com.paytech.risk.decision.domain.runtime.EvaluationInputContract.Field;
import com.paytech.risk.decision.domain.runtime.RuntimeEvaluationFacts;
import com.paytech.risk.decision.domainservice.port.DecisionIdentifierGenerator;
import com.paytech.risk.decision.domainservice.repository.CheckoutInputSnapshotRepository;
import com.paytech.risk.decision.domainservice.repository.DecisionEvaluationRepository;
import com.paytech.risk.decision.domainservice.service.CheckoutRiskAssessmentService;
import com.paytech.risk.decision.service.processor.response.EvaluateDecisionResult;
import com.paytech.risk.decision.service.query.ActiveExecutionReleaseReader;

import lombok.AllArgsConstructor;

/**
 * 结账风控点（CHECKOUT_PREPARE，输入合同 ORDER_PREPARATION v5）评估器
 * <li>业务身份为 Commerce 预分配的订单 ID；同一订单只评估一次，稳定输入相同的重放返回原结果（含 FAILED/TIMED_OUT），否则 409。</li>
 * <li>经营主体来源与 Local Partner 不一致时以参数错误拒绝，在读取发布之前返回且不写评估。</li>
 * <li>读取发布与执行流程都在事务外且只读；一个本地事务保存评估、快照、轨迹与累计事件（仅成功评估）；结账没有附加输出；重放不重新执行、不写累计事件。</li>
 */
@Component
@AllArgsConstructor
public class CheckoutPrepareEvaluator implements DecisionPointEvaluator {
    /** 正整数标识，超过 BIGINT 上限由合同报告为违规。 */
    private static final Pattern POSITIVE_ID = EvaluationInputContract.POSITIVE_ID;

    private static final Pattern AMOUNT = Pattern.compile("(0|[1-9][0-9]{0,15})(\\.[0-9]{1,8})?");

    /** 结账输入合同；字段顺序即查询展示顺序。 */
    private static final EvaluationInputContract CONTRACT = new EvaluationInputContract(
        DecisionPointAuthoringDescriptor.checkoutPrepare().point().pointCode(),
        DecisionPointAuthoringDescriptor.checkoutPrepare().point().inputContractVersion(), POSITIVE_ID,
        List.of(new Field("clientId", true, POSITIVE_ID, 19),
            new Field("entityNo", true, Pattern.compile("[A-Z0-9]{5}"), 5),
            new Field("entitySourceType", true, Pattern.compile("PLATFORM|CLIENT|PARTNER"), 8),
            new Field("localPartnerId", false, POSITIVE_ID, 19),
            new Field("consumerCountryCode", true, Pattern.compile("[A-Z]{2}"), 2),
            new Field("currency", true, Pattern.compile("[A-Z]{3}"), 3),
            new Field("merchandiseAmount", true, AMOUNT, 25), new Field("discountAmount", true, AMOUNT, 25),
            new Field("netAmount", true, AMOUNT, 25),
            new Field("industryCode", true, Pattern.compile("[A-Z][A-Z0-9_]{0,63}"), 64),
            new Field("mccCode", true, Pattern.compile("[0-9]{4}"), 4),
            new Field("lineCount", true, Pattern.compile("[1-9][0-9]{0,2}"), 3)));

    private final RuntimeEvaluationExecutor executor;
    private final DecisionIdentifierGenerator identifiers;
    private final ActiveExecutionReleaseReader activeReleases;
    private final DecisionEvaluationRepository evaluations;
    private final CheckoutRiskAssessmentService service;
    private final CheckoutInputSnapshotRepository snapshots;

    /** 结账输入合同。 */
    @Override
    public EvaluationInputContract contract() {
        return CONTRACT;
    }

    /** 原评估重放，或读取生效发布后执行并保存。 */
    @Override
    public EvaluateDecisionResult evaluate(String businessKey, Map<String, String> inputs) {
        // 输入转换（来源与 Local Partner 不一致时以参数错误拒绝）
        CheckoutRiskInput input = toInput(businessKey, inputs);

        // 原评估重放
        CheckoutInputSnapshot existing = snapshots.findByOrder(input.orderId()).orElse(null);
        if (existing != null) {
            return replay(existing, input);
        }

        // 读取生效发布并在事务外执行
        ExecutionRelease release = activeReleases.require(CONTRACT.pointCode());
        RuntimeEvaluationFacts.Run run =
            executor.execute(release, input, CheckoutRiskFacts.requestId(input.orderId()));

        // 保存与响应
        CheckoutRiskAssessmentContext context =
            CheckoutRiskFacts.build(input, release, run, identifiers::nextIdentifier);
        service.save(context);
        return result(context.getEvaluation());
    }

    /** 按订单查找正式评估。 */
    @Override
    public Optional<Long> findPrimaryEvaluationId(String businessKey) {
        return snapshots.findByOrder(Long.parseLong(businessKey)).map(CheckoutInputSnapshot::getEvaluationId);
    }

    /** 结账输入快照中的订单 ID 与 12 个输入字段；结账没有附加输出。 */
    @Override
    public EvaluationSubject subject(long primaryEvaluationId) {
        CheckoutRiskInput input = input(snapshot(primaryEvaluationId));
        return new EvaluationSubject(Long.toString(input.orderId()), inputs(input), input.contractValues(), Map.of());
    }

    /** 结账输入快照还原的冻结输入；没有附加输出。 */
    @Override
    public ReplaySample replaySample(long primaryEvaluationId) {
        return new ReplaySample(input(snapshot(primaryEvaluationId)), Map.of());
    }

    /** 正式评估的结账输入快照；缺失属于数据损坏。 */
    CheckoutInputSnapshot snapshot(long primaryEvaluationId) {
        return snapshots.findByEvaluation(primaryEvaluationId)
            .orElseThrow(() -> new ErrorException("正式评估缺少结账输入快照: " + primaryEvaluationId));
    }

    /** 统一评估请求的输入字段原值，按合同字段顺序；没有 Local Partner 时为空。 */
    static Map<String, String> inputs(CheckoutRiskInput input) {
        Map<String, String> values = new LinkedHashMap<>();
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

    /** 已通过格式校验的输入转换为冻结输入。 */
    static CheckoutRiskInput toInput(String businessKey, Map<String, String> inputs) {
        String localPartnerId = inputs.get("localPartnerId");
        return new CheckoutRiskInput(Long.parseLong(businessKey), Long.parseLong(inputs.get("clientId")),
            inputs.get("entityNo"), inputs.get("entitySourceType"),
            localPartnerId == null ? null : Long.valueOf(localPartnerId), inputs.get("consumerCountryCode"),
            inputs.get("currency"), new BigDecimal(inputs.get("merchandiseAmount")),
            new BigDecimal(inputs.get("discountAmount")), new BigDecimal(inputs.get("netAmount")),
            inputs.get("industryCode"), inputs.get("mccCode"), Integer.parseInt(inputs.get("lineCount")));
    }

    /** 由结账输入快照还原冻结输入。 */
    static CheckoutRiskInput input(CheckoutInputSnapshot row) {
        return new CheckoutRiskInput(row.getOrderId(), row.getClientId(), row.getEntityNo(), row.getEntitySourceType(),
            row.getLocalPartnerId(), row.getConsumerCountryCode(), row.getCurrency(), row.getMerchandiseAmount(),
            row.getDiscountAmount(), row.getNetAmount(), row.getIndustryCode(), row.getMccCode(), row.getLineCount());
    }

    /** 稳定输入相同才返回原结果。 */
    EvaluateDecisionResult replay(CheckoutInputSnapshot existing, CheckoutRiskInput input) {
        if (!existing.getStableInputHash().equals(CheckoutRiskFacts.stableHash(input))) {
            throw new ConcurrentRequestException("订单已有不同内容的结账风控评估: " + input.orderId());
        }
        DecisionEvaluation evaluation = evaluations.findById(existing.getEvaluationId())
            .orElseThrow(() -> new ErrorException("结账输入快照缺少正式评估: " + existing.getSnapshotId()));
        return result(evaluation);
    }

    /** 由评估构造结论；技术失败返回技术状态；结账没有附加输出。 */
    static EvaluateDecisionResult result(DecisionEvaluation evaluation) {
        String result = evaluation.getTechnicalStatus() == TechnicalStatus.SUCCEEDED
            ? evaluation.getBusinessDecision().getCode() : evaluation.getTechnicalStatus().getCode();
        return new EvaluateDecisionResult(evaluation.getEvaluationId(), result, Map.of());
    }
}

package com.paytech.risk.decision.service.evaluation;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.function.Supplier;

import org.springframework.stereotype.Component;

import com.paytech.risk.decision.core.dal.dataobject.ExecutionRelease;
import com.paytech.risk.decision.domain.enums.TechnicalStatus;
import com.paytech.risk.decision.domain.runtime.DecisionEngine;
import com.paytech.risk.decision.domain.runtime.EngineOutcome;
import com.paytech.risk.decision.domain.runtime.ExecutionSnapshot;
import com.paytech.risk.decision.domain.runtime.FeatureComputationException;
import com.paytech.risk.decision.domain.runtime.FeatureValue;
import com.paytech.risk.decision.domain.runtime.RuntimeEvaluationFacts.Run;
import com.paytech.risk.decision.domain.runtime.RuntimeRiskInput;
import com.paytech.risk.decision.service.query.ExecutionSnapshotReader;
import com.paytech.risk.decision.service.query.FeatureValueReader;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 运行期风控点共用的一次执行：读取执行内容、计算 Feature、执行流程、计时，并把异常与超时转为技术终态
 * <li>正式评估与影子回放共用；全部只读，必须在事务外调用。</li>
 * <li>Feature 计算失败记 FEATURE_ERROR 并保留已完成结果与失败项；其他异常记 EVALUATION_ERROR 并保留已算出的 Feature。</li>
 * <li>超过决策点超时记 TIMED_OUT，保留引擎结果供解释；评估本身不给结论。</li>
 */
@Slf4j
@Component
@AllArgsConstructor
public class RuntimeEvaluationExecutor {
    /** 执行失败代码。 */
    public static final String EVALUATION_ERROR = "EVALUATION_ERROR";

    /** 执行超时代码。 */
    public static final String EVALUATION_TIMEOUT = "EVALUATION_TIMEOUT";

    private final Clock clock;
    private final FeatureValueReader features;
    private final ExecutionSnapshotReader snapshots;

    /**
     * 正式评估：以开始时间为评估时间锚点执行生效发布
     *
     * @param release 生效执行发布；读取执行内容失败同样记为技术失败
     * @param input 冻结输入
     * @param requestId 正式评估的请求身份，仅用于失败日志，不含输入原值
     * @return 执行结果、时间与技术状态
     */
    public Run execute(ExecutionRelease release, RuntimeRiskInput input, String requestId) {
        Instant started = now();
        return run(started, () -> snapshots.read(release), input, started, requestId);
    }

    /**
     * 影子回放：候选发布按样本的冻结输入与原评估时间执行
     *
     * @param snapshot 候选发布的执行内容
     * @param input 样本冻结输入
     * @param evaluationTime 样本的原评估时间，Feature 窗口以它为锚点
     * @return 执行结果、时间与技术状态；候选执行失败是比较结果的一部分，不记错误日志
     */
    public Run replay(ExecutionSnapshot snapshot, RuntimeRiskInput input, Instant evaluationTime) {
        return run(now(), () -> snapshot, input, evaluationTime, null);
    }

    /**
     * 读取内容、计算 Feature 并执行流程；异常与超时转为技术终态
     *
     * @param requestId 正式评估的请求身份；影子回放为空，不记失败日志
     */
    Run run(Instant started, Supplier<ExecutionSnapshot> content, RuntimeRiskInput input, Instant anchor,
        String requestId) {
        Map<Long, FeatureValue> values = null;
        try {
            ExecutionSnapshot snapshot = content.get();
            values = features.read(snapshot, input, anchor);
            EngineOutcome outcome = DecisionEngine.evaluate(snapshot, input, values);
            Instant completed = now();
            if (Duration.between(started, completed).toMillis() > snapshot.timeoutMillis()) {
                return new Run(started, completed, TechnicalStatus.TIMED_OUT, EVALUATION_TIMEOUT, outcome, values);
            }
            return new Run(started, completed, TechnicalStatus.SUCCEEDED, null, outcome, values);
        } catch (FeatureComputationException failure) {
            logFailure(requestId, failure);
            return new Run(started, now(), TechnicalStatus.FAILED, FeatureComputationException.FEATURE_ERROR, null,
                failure.results());
        } catch (RuntimeException failure) {
            // 技术失败是评估合同的一部分：记录为 FAILED 并返回，调用方据此不放行。
            logFailure(requestId, failure);
            return new Run(started, now(), TechnicalStatus.FAILED, EVALUATION_ERROR, null, values);
        }
    }

    /** 正式评估的失败日志；只记请求身份，不记输入原值。 */
    static void logFailure(String requestId, RuntimeException failure) {
        if (requestId != null) {
            log.error("风控评估执行失败, requestId={}", requestId, failure);
        }
    }

    /** UTC 微秒时间。 */
    Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }
}

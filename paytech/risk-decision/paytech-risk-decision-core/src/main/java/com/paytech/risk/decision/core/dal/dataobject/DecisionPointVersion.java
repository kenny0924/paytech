package com.paytech.risk.decision.core.dal.dataobject;

import java.time.Instant;
import com.paytech.risk.decision.domain.enums.DecisionPointVersionStatus;
import com.paytech.risk.decision.domain.enums.DecisionValue;
import com.paytech.risk.decision.domain.enums.EvaluationStrategy;
import com.paytech.risk.decision.domain.enums.FailureStrategy;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NonNull;
import lombok.experimental.Accessors;

/** 冻结的决策事件点执行协议版本。 */
@Getter
@AllArgsConstructor
@EqualsAndHashCode
@Accessors(fluent = true)
public class DecisionPointVersion {

    /** 事件点协议版本的稳定业务标识。 */
    private long pointVersionId;

    /** 所属决策事件点的稳定业务标识。 */
    private long decisionPointId;

    /** 事件点协议版本号。 */
    private int versionNo;

    /** Risk 受控目录中的输入契约编码。 */
    @NonNull
    private String inputContractCode;

    /** 输入契约版本号。 */
    private int inputContractVersion;

    /** Risk 受控目录中的输出契约编码。 */
    @NonNull
    private String outputContractCode;

    /** 未命中终态规则时使用的默认决定。 */
    @NonNull
    private DecisionValue defaultDecision;

    /** 事件点的固定评估策略。 */
    @NonNull
    private EvaluationStrategy evaluationStrategy;

    /** 技术失败时的固定失败策略。 */
    @NonNull
    private FailureStrategy failureStrategy;

    /** 评估超时上限，单位毫秒。 */
    private int timeoutMillis;

    /** 事件点协议版本状态。 */
    @NonNull
    private DecisionPointVersionStatus status;

    /** 协议内容的 SHA-256 小写十六进制哈希。 */
    @NonNull
    private String contentHash;

    /** 记录的 UTC 微秒创建时间。 */
    @NonNull
    private Instant createTime;

    /** 可变记录最后一次持久化更新的 UTC 微秒时间。 */
    @NonNull
    private Instant updateTime;

    /** 仅在 Repository 已确认退役 CAS 成功后同步原对象。 */
    public void markRetired(Instant time) {
        status = DecisionPointVersionStatus.RETIRED;
        updateTime = time;
    }

    /** 仅在 Repository 已确认冻结 CAS 成功后同步原对象。 */
    public void markFrozen(Instant time) {
        status = DecisionPointVersionStatus.FROZEN;
        updateTime = time;
    }
}

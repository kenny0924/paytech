package com.paytech.risk.decision.core.dal.dataobject;

import com.paytech.risk.decision.domain.enums.RuleTraceType;
import java.time.Instant;

import lombok.Data;

/** 规则与条件谓词的可解释轨迹（t_evaluation_rule_trace）。 */
@Data
public class EvaluationRuleTrace {
    /** 轨迹 ID。 */
    private long traceId;

    /** 评估 ID。 */
    private long evaluationId;

    /** 行类型。 */
    private RuleTraceType traceType;

    /** 规则集版本 ID；RULE 行非空。 */
    private Long ruleSetVersionId;

    /** 规则 ID。 */
    private long ruleId;

    /** 条件节点 ID；PREDICATE 行非空。 */
    private Long nodeId;

    /** 同类型内的执行顺序。 */
    private int executionSequence;

    /** 命中或谓词结果。 */
    private Boolean resultValue;

    /** 规则是否以终态动作结束所在规则集；仅 RULE 行。 */
    private Boolean terminalFlag;

    /** 耗时毫秒；一期不逐条计时，为空。 */
    private Integer elapsedMillis;

    /** 失败代码。 */
    private String failureCode;

    /** RULE 行唯一槽位，固定 true；PREDICATE 行为空。 */
    private Boolean ruleResultSlot;

    /** PREDICATE 行唯一槽位，固定 true；RULE 行为空。 */
    private Boolean predicateResultSlot;

    /** 创建 UTC 时间。 */
    private Instant createTime;
}

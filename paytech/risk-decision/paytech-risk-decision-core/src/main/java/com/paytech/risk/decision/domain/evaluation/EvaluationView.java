package com.paytech.risk.decision.domain.evaluation;

import java.time.Instant;
import java.util.Map;

import com.paytech.risk.decision.domain.enums.DecisionEnvironment;
import com.paytech.risk.decision.domain.enums.EvaluationMode;

/**
 * 一次评估的结论及其发布与流程坐标；组合评估、发布、流程版本与输入快照
 *
 * @param evaluationId 评估 ID
 * @param pointCode 风控点编码
 * @param businessKey 业务身份；影子评估取其基线评估的业务身份
 * @param mode 正式或影子
 * @param result ALLOW、REVIEW、DENY，或技术状态 FAILED、TIMED_OUT
 * @param reasonCode 到达结束节点的原因码；放行、失败或没有路径时为空
 * @param reasonMessage 到达结束节点的原因文案；同上
 * @param outputs 附加输出；仅放行时有值
 * @param failureCode 失败代码；仅 FAILED、TIMED_OUT 有值
 * @param releaseId 执行发布 ID
 * @param environment 发布环境
 * @param flowId 决策流 ID
 * @param flowCode 决策流编码
 * @param flowVersionId 决策流版本 ID
 * @param flowVersionNo 决策流版本号
 * @param evaluationTime 评估时间
 * @param elapsedMillis 执行耗时毫秒
 * @param baselineEvaluationId 影子评估的基线评估 ID；正式评估为空
 * @param shadowRunId 影子批次 ID；正式评估为空
 * @param pathAvailable 是否记录了流程路径
 */
public record EvaluationView(long evaluationId, String pointCode, String businessKey, EvaluationMode mode,
    String result, String reasonCode, String reasonMessage, Map<String, String> outputs, String failureCode,
    long releaseId, DecisionEnvironment environment, long flowId, String flowCode, long flowVersionId,
    int flowVersionNo, Instant evaluationTime, int elapsedMillis, Long baselineEvaluationId, Long shadowRunId,
    boolean pathAvailable) { }

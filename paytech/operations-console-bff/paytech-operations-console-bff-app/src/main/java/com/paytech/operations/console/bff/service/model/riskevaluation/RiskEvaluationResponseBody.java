package com.paytech.operations.console.bff.service.model.riskevaluation;

import java.util.Map;

import lombok.Getter;
import lombok.Setter;

/**
 * 风控评估结论页面模型；字段与 Risk 结论合同一一对应，编码类字段未知值原样返回。
 */
@Getter
@Setter
public class RiskEvaluationResponseBody {
    /**
     * 评估 ID
     */
    private String evaluationId;

    /**
     * 风控点编码
     */
    private String pointCode;

    /**
     * 业务身份
     * <li>支付为支付尝试 ID；影子评估为其基线评估的业务身份。</li>
     */
    private String businessKey;

    /**
     * 评估模式
     * <li>PRIMARY、SHADOW。</li>
     */
    private String mode;

    /**
     * 结论
     * <li>ALLOW、REVIEW、DENY、FAILED、TIMED_OUT。</li>
     */
    private String result;

    /**
     * 原因码；放行或没有路径时为空
     */
    private String reasonCode;

    /**
     * 原因文案
     */
    private String reasonMessage;

    /**
     * 附加输出（如认证要求）；非放行结论为空对象
     */
    private Map<String, String> outputs;

    /**
     * 失败码；FAILED、TIMED_OUT 时有值
     */
    private String failureCode;

    /**
     * 执行发布 ID
     */
    private String releaseId;

    /**
     * 发布环境
     */
    private String environment;

    /**
     * 决策流 ID
     */
    private String flowId;

    /**
     * 决策流编码
     */
    private String flowCode;

    /**
     * 决策流版本 ID
     */
    private String flowVersionId;

    /**
     * 决策流版本号
     */
    private int flowVersionNo;

    /**
     * 评估时间（UTC ISO-8601）
     */
    private String evaluationTime;

    /**
     * 评估耗时（毫秒）
     */
    private int elapsedMillis;

    /**
     * 基线评估 ID；仅影子评估有值
     */
    private String baselineEvaluationId;

    /**
     * 影子运行 ID；仅影子评估有值
     */
    private String shadowRunId;

    /**
     * 是否记录了流程路径
     * <li>早于路径记录上线的评估为 false。</li>
     */
    private boolean pathAvailable;
}

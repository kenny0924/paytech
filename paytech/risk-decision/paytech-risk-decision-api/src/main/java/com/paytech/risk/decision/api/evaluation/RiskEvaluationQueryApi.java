package com.paytech.risk.api.evaluation;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;

import com.paytech.risk.api.evaluation.response.EvaluationExplanationResponseBody;
import com.paytech.risk.api.evaluation.response.EvaluationResponseBody;

/**
 * 评估结论与解释的只读查询合同；内部直连，供运营后台经 BFF 排查
 * <li>与统一评估接口同属 /risk-decisions/v1，单独成接口，评估调用方（如 Payment）的客户端与测试桩不受影响。</li>
 * <li>响应含 IP、卡指纹等受限输入原值；该路径的 Web 日志只记元数据，调用方同样不得记录响应体。</li>
 */
@HttpExchange("/risk-decisions/v1")
public interface RiskEvaluationQueryApi {

    /**
     * 按评估 ID 查询评估结论
     *
     * @param evaluationId
     *            评估 ID，正整数十进制字符串
     * @return 结论、原因、附加输出与发布、流程坐标
     *         <li>正式与影子评估都可查询；评估不存在返回 404 NOT_FOUND，ID 格式不符返回 400。</li>
     */
    @GetExchange("/evaluations/{evaluationId}")
    EvaluationResponseBody getEvaluation(@PathVariable("evaluationId") String evaluationId);

    /**
     * 按风控点与业务身份查询正式评估的结论
     *
     * @param pointCode
     *            风控点编码，必填；未登记返回 400
     * @param businessKey
     *            业务身份（支付为支付尝试 ID），必填；格式按该点合同校验，不符返回 400
     * @return 与按评估 ID 查询相同；该业务身份没有评估返回 404 NOT_FOUND
     */
    @GetExchange("/evaluations")
    EvaluationResponseBody findEvaluation(@RequestParam(value = "pointCode", required = false) String pointCode,
        @RequestParam(value = "businessKey", required = false) String businessKey);

    /**
     * 查询评估解释
     *
     * @param evaluationId
     *            评估 ID，正整数十进制字符串
     * @return 冻结输入原值、流程路径、执行过的规则与谓词结果、变量取值、影子比较
     *         <li>名称与编码取自评估所用发布的冻结内容；常量与集合为当前值并标注 CURRENT。</li>
     *         <li>评估不存在返回 404 NOT_FOUND，ID 格式不符返回 400。</li>
     */
    @GetExchange("/evaluations/{evaluationId}/explanation")
    EvaluationExplanationResponseBody getExplanation(@PathVariable("evaluationId") String evaluationId);
}

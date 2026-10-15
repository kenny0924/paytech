package com.paytech.risk.decision.service.evaluation;

import java.util.Map;

import com.paytech.risk.decision.domain.runtime.EvaluationInputContract;
import com.paytech.risk.decision.service.processor.response.EvaluateDecisionResult;

/**
 * 一个同步风控点的评估适配器：输入转换、重放判断、执行与快照保存
 * <li>由统一评估 Processor 在完成合同校验并持有业务身份锁后调用；新增同步风控点只新增实现，不新增接口。</li>
 */
public interface DecisionPointEvaluator {

    /**
     * 本风控点的输入合同
     *
     * @return 风控点编码、合同版本、业务身份格式与字段规则
     */
    EvaluationInputContract contract();

    /**
     * 在业务身份锁内评估
     *
     * @param businessKey 已通过格式校验的业务身份
     * @param inputs 已通过格式校验的输入字段
     * @return 原评估（重放）或本次评估的结论
     *         <li>同一业务身份内容不一致抛出并发冲突；没有生效发布抛出未配置异常且不写评估。</li>
     */
    EvaluateDecisionResult evaluate(String businessKey, Map<String, String> inputs);
}

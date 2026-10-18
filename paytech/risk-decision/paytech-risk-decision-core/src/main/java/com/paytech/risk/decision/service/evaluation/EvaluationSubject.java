package com.paytech.risk.decision.service.evaluation;

import java.util.Map;

/**
 * 一次正式评估冻结的业务身份、输入与已保存的输出，供查询与解释
 *
 * @param businessKey 业务身份（支付为支付尝试 ID）
 * @param inputs 统一评估请求的全部输入字段原值，按合同字段顺序；未提供的字段值为空
 * @param contractValues 输入变量按来源字段取值的规范值，与评估时引擎读取的一致
 * @param outputs 已保存的附加输出；非放行时为空
 */
public record EvaluationSubject(String businessKey, Map<String, String> inputs, Map<String, Object> contractValues,
    Map<String, String> outputs) { }

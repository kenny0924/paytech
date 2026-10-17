package com.paytech.risk.decision.service.evaluation;

import java.util.Map;

import com.paytech.risk.decision.domain.runtime.RuntimeRiskInput;

/**
 * 影子回放的一个样本：正式评估冻结的输入与已保存的附加输出
 *
 * @param input 冻结输入
 * @param outputs 已保存的附加输出；非放行或该风控点没有附加输出时为空
 */
public record ReplaySample(RuntimeRiskInput input, Map<String, String> outputs) { }

package com.paytech.risk.decision.service.processor.request;

import java.util.Map;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 统一风控评估的 Owner 输入；风控点、合同版本与输入格式由 Processor 按登记合同校验。 */
@Getter
@AllArgsConstructor
public class EvaluateDecisionRequest {
    /** 风控点编码。 */
    private final String pointCode;

    /** 输入合同版本。 */
    private final int contractVersion;

    /** 风控点内唯一的业务身份。 */
    private final String businessKey;

    /** 输入字段；值为空等同于未提供。 */
    private final Map<String, String> inputs;
}

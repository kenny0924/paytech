package com.paytech.operations.console.bff.service.model.riskunderwriting;

import com.paytech.operations.console.bff.domain.enums.UnderwritingInputStatus;
import com.paytech.risk.decision.api.control.request.UnderwritingInputBody;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 按 Client 从 Owner 读取组装的承保输入：Client 摘要、输入快照、输入状态与各来源读取结果。 */
@Getter
@AllArgsConstructor
public class UnderwritingInputAssembly {
    /** Client 主档摘要。 */
    private final UnderwritingClientBody client;

    /** 输入快照；缺失项为 null。 */
    private final UnderwritingInputBody input;

    /** 输入状态。 */
    private final UnderwritingInputStatus status;

    /** 各来源读取结果。 */
    private final UnderwritingSourcesBody sources;
}

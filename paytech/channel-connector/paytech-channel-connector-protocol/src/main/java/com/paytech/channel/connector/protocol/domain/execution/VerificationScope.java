package com.paytech.channel.connector.protocol.domain.execution;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 沙箱验证运行的执行附加项（沙箱验证技术方案第 2 节）：初始化 CHANNEL_ACCOUNT_CONFIG 改读验证账号，并记录轨迹。
 * 正式执行不传，行为与响应不变。
 */
@Getter
@AllArgsConstructor
public class VerificationScope {

    /** 验证账号材料。 */
    private final VerifyAccountMaterial account;

    /** 本步骤的执行轨迹。 */
    private final ExecutionTrace trace;
}

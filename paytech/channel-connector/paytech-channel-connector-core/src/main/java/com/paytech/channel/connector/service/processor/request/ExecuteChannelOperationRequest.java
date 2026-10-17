package com.paytech.payment.channel.service.processor.request;

import java.util.Map;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

/** 经 Platform 执行一次渠道操作；不得携带卡值或账号材料，卡只以支付工具 Token 出现在输入中。 */
@Getter
@Setter
public class ExecuteChannelOperationRequest {
    /** 冻结坐标与用途，按执行配置规则校验。 */
    @Valid
    @NotNull
    private GetChannelExecutionConfigRequest target;

    /** Platform 标准 API 编码，例如 PAY。 */
    @NotNull
    @Pattern(regexp = "[A-Z][A-Z0-9_]{0,63}")
    private String standardApiCode;

    /**
     * 标准 API 业务输入
     * <li>不含 channelAccount；Processor 按冻结账号补齐账号身份与 Vault 引用。</li>
     */
    @NotNull
    private Map<String, Object> input;

    @Override
    public String toString() {
        return "ExecuteChannelOperationRequest[" + standardApiCode + ", input redacted]";
    }
}

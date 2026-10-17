package com.paytech.channel.connector.api.operation.request;

import java.util.Map;

import com.paytech.channel.connector.api.candidate.request.MidCriteriaBody;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

/**
 * 在冻结的渠道三元组上执行一次渠道操作。
 * <li>channel-connector 重新校验三元组的归属、状态与 OE：账号与 MID 的 entityNo 必须等于本请求的 entityNo，不一致返回 NOT_SENT，绝不重选。</li>
 * <li>input 是 Platform 标准 API 的业务输入（支付工具句柄、尝试标识、金额、回跳地址等），不含卡值与账号材料；账号身份由 channel-connector 补齐。</li>
 */
@Getter
@Setter
public class ExecuteChannelOperationRequestBody {
    /** 交易冻结的 Operating Entity 编号。 */
    @NotNull
    @Pattern(regexp = "[0-9A-Za-z]{5}")
    private String entityNo;

    /** 冻结渠道 ID，十进制字符串。 */
    @NotNull
    @Pattern(regexp = "[1-9][0-9]{0,18}")
    private String channelId;

    /** 冻结渠道技术账号 ID，十进制字符串；原交易不得替换为 MID 当前账号。 */
    @NotNull
    @Pattern(regexp = "[1-9][0-9]{0,18}")
    private String channelAccountId;

    /** 冻结 MID ID，十进制字符串。 */
    @NotNull
    @Pattern(regexp = "[1-9][0-9]{0,18}")
    private String midId;

    /** 渠道操作代码，取值见 ChannelOperation。 */
    @NotNull
    @Pattern(regexp = "[A-Z][A-Z0-9_]{0,63}")
    private String operation;

    /** 执行用途代码，取值见 ExecutionPurpose；新支付重新匹配当前能力与金额上下限。 */
    @NotNull
    @Pattern(regexp = "[A-Z][A-Z0-9_]{0,63}")
    private String purpose;

    /** 新支付必填的交易维度；原交易后续操作与恢复为空。 */
    @Valid
    private MidCriteriaBody criteria;

    /** 新支付必填：交易币种主单位正金额的十进制字符串，最多 8 位小数。 */
    @Pattern(regexp = "(0|[1-9][0-9]{0,15})(\\.[0-9]{1,8})?")
    private String amount;

    /** Platform 标准 API 业务输入，不含 channelAccount。 */
    @NotNull
    private Map<String, Object> input;

    @Override
    public String toString() {
        return "ExecuteChannelOperationRequestBody[" + operation + ", input redacted]";
    }
}

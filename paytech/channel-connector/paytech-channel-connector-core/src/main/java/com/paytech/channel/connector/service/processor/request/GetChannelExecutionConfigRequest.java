package com.paytech.channel.connector.service.processor.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import com.paytech.channel.connector.api.enums.ChannelOperation;
import com.paytech.channel.connector.api.enums.ExecutionPurpose;
import lombok.Getter;
import lombok.Setter;

/** Channel 内部业务合同。 */
@Getter
@Setter
public class GetChannelExecutionConfigRequest {
    /** 可信调用方提供的冻结 OE。 */
    @NotNull @Pattern(regexp = "[0-9A-Za-z]{5}")
    private String entityNo;

    /** 冻结渠道 ID。 */
    @NotNull @Positive
    private Long channelId;

    /** 冻结技术账号 ID，原交易不得替换为 MID 当前账号。 */
    @NotNull @Positive
    private Long channelAccountId;

    /** 冻结 MID ID。 */
    @NotNull @Positive
    private Long midId;

    /** 本次执行的渠道操作。 */
    @NotNull
    private ChannelOperation operation;

    /** 新支付、原交易后续操作或原操作恢复；由可信执行边界确定。 */
    @NotNull
    private ExecutionPurpose purpose;

    /** 新支付必填；原交易与恢复不按当前准入能力重新选路。 */
    @Valid
    private MidCriteria criteria;

    /** 新支付必填；交易币种主单位正金额。 */
    @DecimalMin(value = "0", inclusive = false) @Digits(integer = 16, fraction = 8)
    private BigDecimal amount;
}

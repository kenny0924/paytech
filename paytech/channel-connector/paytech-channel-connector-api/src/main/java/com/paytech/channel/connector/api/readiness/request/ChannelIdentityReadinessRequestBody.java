package com.paytech.channel.connector.api.readiness.request;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/** 判定某 Operating Entity 下若干组支付方式 × 币种 × MCC 的渠道身份是否就绪。 */
@Getter
@Setter
public class ChannelIdentityReadinessRequestBody {
    /** Operating Entity 编号，区分大小写；只读取该 OE 的渠道账号与 MID。 */
    @NotNull
    @Pattern(regexp = "[0-9A-Za-z]{5}")
    private String entityNo;

    /**
     * 待判定的组合
     * <li>1–200 组；同组重复时返回同一结论。</li>
     */
    @NotNull
    @Size(min = 1, max = 200)
    private List<@NotNull @Valid ChannelIdentityReadinessItemBody> items;
}

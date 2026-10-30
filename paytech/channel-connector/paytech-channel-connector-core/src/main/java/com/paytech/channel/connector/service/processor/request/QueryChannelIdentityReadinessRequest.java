package com.paytech.channel.connector.service.processor.request;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/** 渠道身份就绪判定的内部业务合同。 */
@Getter
@Setter
public class QueryChannelIdentityReadinessRequest {
    /** OE 编号，区分大小写。 */
    @NotNull @Pattern(regexp = "[0-9A-Za-z]{5}")
    private String entityNo;

    /** 1–200 组待判定组合；同组重复返回同一结论。 */
    @NotNull @Size(min = 1, max = 200)
    private List<@NotNull @Valid ChannelIdentityReadinessItem> items;
}

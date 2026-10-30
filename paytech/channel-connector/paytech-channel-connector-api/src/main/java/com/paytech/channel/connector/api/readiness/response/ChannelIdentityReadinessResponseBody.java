package com.paytech.channel.connector.api.readiness.response;

import java.util.List;

import lombok.Getter;
import lombok.Setter;

/** 渠道身份就绪查询结果。 */
@Getter
@Setter
public class ChannelIdentityReadinessResponseBody {
    /** 请求的 Operating Entity 编号。 */
    private String entityNo;

    /** 按请求顺序逐组的结论，条数与请求相同。 */
    private List<ChannelIdentityReadinessResultBody> items;
}

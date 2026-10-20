package com.paytech.channel.connector.domain;

import lombok.Getter;
import lombok.Setter;

/** 一个渠道成本版本的适用范围数与成本组成数；由版本、范围、组成三表同库汇总查询得到。 */
@Getter
@Setter
public class ChannelCostVersionContentCount {
    /** 版本主键。 */
    private Long costVerId;

    /** 版本下适用范围数。 */
    private int scopeCount;

    /** 版本下全部适用范围的成本组成数。 */
    private int componentCount;
}

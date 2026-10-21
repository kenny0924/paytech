package com.paytech.channel.connector.protocol.core.dal.dataobject;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 按接入方分组的下级配置数量，只读聚合结果。 */
@Getter
@Setter
@NoArgsConstructor
public class IntegrationChildCount {

    /** 接入方标识。 */
    private Long integrationId;

    /** 该接入方下的配置行数。 */
    private long childCount;
}

package com.paytech.channel.connector.protocol.core.dal.dataobject;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 原子 API 执行定义中引用的出站地址代码，只读投影。 */
@Getter
@Setter
@NoArgsConstructor
public class ChannelApiEndpoint {

    /** 原子 API 编码。 */
    private String apiCode;

    /**
     * 执行定义 transport.endpointCode 的值。
     * <li>定义缺少该路径时为 null。</li>
     */
    private String endpointCode;
}

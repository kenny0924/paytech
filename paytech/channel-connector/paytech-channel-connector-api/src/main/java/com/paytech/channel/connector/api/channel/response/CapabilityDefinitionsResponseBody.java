package com.paytech.channel.connector.api.channel.response;

import java.util.List;

import lombok.Getter;
import lombok.Setter;

/** 已注册的 MID 能力目录；来自本服务配置 channel-connector.capability-definitions。 */
@Getter
@Setter
public class CapabilityDefinitionsResponseBody {
    /** 能力定义，按能力代码升序 */
    private List<CapabilityDefinitionResponseBody> definitions;
}

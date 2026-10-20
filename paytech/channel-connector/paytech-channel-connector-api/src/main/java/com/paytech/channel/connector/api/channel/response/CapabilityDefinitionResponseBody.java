package com.paytech.channel.connector.api.channel.response;

import java.util.List;

import lombok.Getter;
import lombok.Setter;

/** 一项已注册的能力定义。 */
@Getter
@Setter
public class CapabilityDefinitionResponseBody {
    /** 能力代码，例如 ACTION.PAY */
    private String capabilityCode;

    /** 值类型：BOOLEAN、DECIMAL、CODE */
    private String valueType;

    /** CODE 类型的允许值，升序；其他类型为空列表 */
    private List<String> allowedCodes;
}

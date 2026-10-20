package com.paytech.channel.connector.api.channel.response;

import lombok.Getter;
import lombok.Setter;

/** 能力组内一项能力明细；三个值字段恰有一个非空。 */
@Getter
@Setter
public class MidCapabilityDetailResponseBody {
    /** 能力代码 */
    private String capabilityCode;

    /** 值类型：BOOLEAN、DECIMAL、CODE */
    private String valueType;

    /** BOOLEAN 类型的值 */
    private Boolean booleanValue;

    /** DECIMAL 类型的值，去掉末尾零的普通十进制字符串 */
    private String decimalValue;

    /** CODE 类型的目录值 */
    private String codeValue;
}

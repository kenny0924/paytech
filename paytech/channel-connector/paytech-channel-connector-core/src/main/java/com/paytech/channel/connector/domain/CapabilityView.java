package com.paytech.channel.connector.domain;

import java.math.BigDecimal;
import com.paytech.channel.connector.core.enums.CapabilityValueType;
import lombok.Getter;
import lombok.Setter;

/** Channel 内部业务合同。 */
@Getter
@Setter
public class CapabilityView {
    /** 能力目录代码。 */
    private String capabilityCode;

    /** 值类型，三种值恰有一个。 */
    private CapabilityValueType valueType;

    /** BOOLEAN 类型值，false 是明确不支持。 */
    private Boolean booleanValue;

    /** DECIMAL 类型值，金额按交易币种主单位。 */
    private BigDecimal decimalValue;

    /** CODE 类型的目录值。 */
    private String codeValue;
}

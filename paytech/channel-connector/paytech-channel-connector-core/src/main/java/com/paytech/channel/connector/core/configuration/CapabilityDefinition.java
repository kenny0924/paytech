package com.paytech.channel.connector.core.configuration;

import java.util.Set;
import jakarta.validation.constraints.NotNull;
import com.paytech.channel.connector.core.enums.CapabilityValueType;
import lombok.Getter;
import lombok.Setter;

/** Channel 内部业务合同。 */
@Getter
@Setter
public class CapabilityDefinition {
    /** 受控能力的值类型。 */
    @NotNull
    private CapabilityValueType valueType;

    /** CODE 类型允许值集合；非 CODE 类型为空。 */
    private Set<String> allowedCodes;
}

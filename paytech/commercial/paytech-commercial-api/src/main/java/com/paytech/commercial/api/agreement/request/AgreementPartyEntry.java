package com.paytech.commercial.api.agreement.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import lombok.Getter;
import lombok.Setter;

/** 合同参与方的一个角色：同一 Party 可承担多个角色，签署时同 Party 全部角色一次原子登记。 */
@Getter
@Setter
public class AgreementPartyEntry {
    /** 法律 Party ID（client-master 值引用） */
    @NotBlank(message = "partyId 必填")
    @Pattern(regexp = "[1-9][0-9]{0,18}", message = "partyId 必须是规范正十进制 ID")
    private String partyId;

    /**
     * Party 资料版本 ID（client-master 值引用）
     * <li>同一 Party 的各角色必须引用同一资料版本。</li>
     */
    @NotBlank(message = "partyProfileId 必填")
    @Pattern(regexp = "[1-9][0-9]{0,18}", message = "partyProfileId 必须是规范正十进制 ID")
    private String partyProfileId;

    /**
     * Party 在合同中的角色
     * <li>CLIENT 合同须含 CLIENT 角色且不含 LOCAL_PARTNER，PARTNER 合同反之。</li>
     */
    @NotBlank(message = "partyRole 必填")
    @Pattern(regexp = "CLIENT|LOCAL_PARTNER|PLATFORM_SIGNATORY|OPERATING_ENTITY", message = "partyRole 只能为 CLIENT、LOCAL_PARTNER、PLATFORM_SIGNATORY、OPERATING_ENTITY")
    private String partyRole;
}

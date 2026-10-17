package com.paytech.commercial.api.agreement.response;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 已被合同参与方引用的 Party 与资料。 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AgreementPartyUsageResponseBody {
    /** 请求中被引用的 Party ID，升序去重。 */
    private List<String> referencedPartyIds;

    /** 请求中被引用的资料 ID，升序去重。 */
    private List<String> referencedPartyProfileIds;
}

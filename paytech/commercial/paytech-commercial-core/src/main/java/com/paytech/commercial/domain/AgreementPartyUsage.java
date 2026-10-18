package com.paytech.commercial.domain;

import java.util.Set;

import lombok.Value;
import lombok.experimental.Accessors;

/** 被合同参与方引用的 Party 与资料。 */
@Value
@Accessors(fluent = true)
public class AgreementPartyUsage {
    /** 被引用的 Party ID。 */
    Set<Long> partyIds;
    /** 被引用的资料 ID。 */
    Set<Long> partyProfileIds;
}

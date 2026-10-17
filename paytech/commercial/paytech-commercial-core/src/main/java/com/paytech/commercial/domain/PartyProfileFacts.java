package com.paytech.commercial.domain;

import java.time.Instant;

import lombok.Value;
import lombok.experimental.Accessors;

/** client-master 返回的合同参与方资料，用于签署校验与法律证据。 */
@Value
@Accessors(fluent = true)
public class PartyProfileFacts {
    /** 资料 ID。 */
    Long profileId;
    /** 所属 Party ID。 */
    Long partyId;
    /** 首次批准时间。 */
    Instant approvedTime;
    /** 仍为草稿时的申请 ID。 */
    Long draftApplicationId;
    /** 资料内容 Hash。 */
    String contentHash;
}

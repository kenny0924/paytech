package com.paytech.risk.decision.api.control.response;

import lombok.Getter;
import lombok.Setter;

/** 一个 Client 的迁移结果。 */
@Getter
@Setter
public class TierMigrationResultBody {

    /** Client ID。 */
    private String clientId;

    /** CREATED（已设为 STANDARD）或 SKIPPED_EXISTING（已有档位，未改动）。 */
    private String outcome;
}

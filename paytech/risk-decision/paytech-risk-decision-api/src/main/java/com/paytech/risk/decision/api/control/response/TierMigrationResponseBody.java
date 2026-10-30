package com.paytech.risk.decision.api.control.response;

import java.util.List;

import lombok.Getter;
import lombok.Setter;

/** 存量迁移结果，按请求顺序每个 Client 一项。 */
@Getter
@Setter
public class TierMigrationResponseBody {

    /** 迁移结果。 */
    private List<TierMigrationResultBody> results;
}

package com.paytech.risk.decision.api.control.response;

import java.util.List;

import lombok.Getter;
import lombok.Setter;

/** 某 Client 的承保版本历史，版本号倒序，最多 100 个。 */
@Getter
@Setter
public class UnderwritingHistoryResponseBody {

    /** 各版本详情。 */
    private List<UnderwritingResponseBody> items;
}

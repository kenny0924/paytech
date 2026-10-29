package com.paytech.risk.decision.api.control.response;

import lombok.Getter;
import lombok.Setter;

/** 某 Client 当前的承保：有效结论、进行中版本与最新版本，三者均可为空。 */
@Getter
@Setter
public class ClientUnderwritingCurrentResponseBody {

    /** 当前有效的 APPROVED 版本。 */
    private UnderwritingResponseBody effective;

    /** 进行中的 DRAFT 或 SUBMITTED 版本。 */
    private UnderwritingResponseBody open;

    /** 版本号最大的版本。 */
    private UnderwritingResponseBody latest;
}

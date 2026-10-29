package com.paytech.operations.console.bff.service.model.riskunderwriting;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Client 当前承保的页面模型：有效、进行中与最新版本，均可为 null。 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UnderwritingCurrentPageBody {
    /** 当前有效的 APPROVED 版本。 */
    private LabeledUnderwritingResponseBody effective;

    /** 进行中的 DRAFT 或 SUBMITTED 版本。 */
    private LabeledUnderwritingResponseBody open;

    /** 版本号最大的版本。 */
    private LabeledUnderwritingResponseBody latest;
}

package com.paytech.operations.console.bff.service.model.riskunderwriting;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 承保版本历史的页面模型，版本号倒序。 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UnderwritingHistoryPageBody {
    /** 各版本。 */
    private List<LabeledUnderwritingResponseBody> items;
}

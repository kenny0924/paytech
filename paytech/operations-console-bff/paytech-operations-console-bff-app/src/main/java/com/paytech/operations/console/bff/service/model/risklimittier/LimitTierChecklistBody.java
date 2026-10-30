package com.paytech.operations.console.bff.service.model.risklimittier;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 上线复核清单系统部分：四个系统项（固定顺序）与其事实值。 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class LimitTierChecklistBody {
    /** 四个系统项，按 QUALIFICATION、UNDERWRITING、AGREEMENT、CAPABILITIES 顺序。 */
    private List<LimitTierCheckItemBody> items;

    /** 事实值。 */
    private LimitTierChecklistFactsBody facts;
}

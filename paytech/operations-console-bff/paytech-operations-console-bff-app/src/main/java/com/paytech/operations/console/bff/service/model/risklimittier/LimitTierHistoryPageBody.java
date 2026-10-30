package com.paytech.operations.console.bff.service.model.risklimittier;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 档位变更历史页面模型，顺序同 Risk（申请时间倒序）。 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class LimitTierHistoryPageBody {
    /** 带显示名的变更记录。 */
    private List<LabeledTierChangeBody> items;
}

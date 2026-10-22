package com.paytech.platform.support.api.dataquery.response;

import lombok.Getter;
import lombok.Setter;

/** 结果列：列名与显示标题；HIDE 列不出现。 */
@Getter
@Setter
public class DataQueryColumn {

    /** 结果列名（列标签）。 */
    private String name;

    /** 显示标题；未声明时等于列名。 */
    private String label;
}

package com.paytech.platform.support.api.dataquery.response;

import lombok.Getter;
import lombok.Setter;

/** 节点结果列：列名、标题及联合查询标注；HIDE 列不出现。 */
@Getter
@Setter
public class JointQueryNodeColumn {

    /** 结果列名（列标签）。 */
    private String name;

    /** 显示标题；未声明时等于列名。 */
    private String label;

    /** 标注的键类型编码；未标注为 null。 */
    private String keyType;

    /** 标注的角色：LINK、DIMENSION；未标注为 null。 */
    private String keyRole;
}

package com.paytech.platform.support.api.dataquery.response;

import lombok.Getter;
import lombok.Setter;

/** 结果行中一个 LINK 列的非空值；页面据此画边，目标为下一层 triggerValues 含该值的节点。 */
@Getter
@Setter
public class JointQueryLink {

    /** 来源节点 ID。 */
    private String nodeId;

    /** 来源行下标，从 0 开始。 */
    private int rowIndex;

    /** 来源列名。 */
    private String column;

    /** 该列标注的键类型编码。 */
    private String keyType;

    /** 规范化后的键值。 */
    private String value;
}

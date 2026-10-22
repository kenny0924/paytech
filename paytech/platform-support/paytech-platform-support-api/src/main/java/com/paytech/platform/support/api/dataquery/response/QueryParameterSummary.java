package com.paytech.platform.support.api.dataquery.response;

import lombok.Getter;
import lombok.Setter;

/** 参数声明摘要，供列表显示“* 必填、[] 集合、⟨片段⟩”。 */
@Getter
@Setter
public class QueryParameterSummary {

    /** 参数名。 */
    private String name;

    /** 类型：STRING、INTEGER、DECIMAL、BOOLEAN、DATE、DATETIME、ENUM、FRAGMENT、LIST。 */
    private String type;

    /** LIST 的元素类型；非 LIST 为 null。 */
    private String elementType;

    /** 是否必填。 */
    private boolean required;
}

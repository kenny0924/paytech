package com.paytech.operations.console.bff.service.model.dataquery.response;

import java.util.List;

import lombok.Getter;
import lombok.Setter;

/** 因对象已加载而未执行的“按自身 ID 查”视图；页面可用 templateCodes 按需单独执行。 */
@Getter
@Setter
public class JointQueryAlreadyLoaded {

    /** 模板编码。 */
    private String templateCode;

    /** 模板名称。 */
    private String templateName;

    /** 触发键类型，即该模板的行主体键类型。 */
    private String keyType;

    /** 因已加载而未用于执行的值（规范化后）。 */
    private List<String> values;
}

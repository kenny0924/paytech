package com.paytech.platform.support.api.dataquery.response;

import lombok.Getter;
import lombok.Setter;

/** 配置了联合查询却不参与的模板。 */
@Getter
@Setter
public class JointQueryExcludedTemplate {

    /** 模板编码。 */
    private String templateCode;

    /**
     * 不参与原因码
     * <li>KEY_PARAMETER_NOT_REQUIRED、OTHER_REQUIRED_PARAMETER、TEMPLATE_INACTIVE、DATA_SOURCE_INACTIVE。</li>
     */
    private String reasonCode;

    /** 中文原因。 */
    private String reason;
}

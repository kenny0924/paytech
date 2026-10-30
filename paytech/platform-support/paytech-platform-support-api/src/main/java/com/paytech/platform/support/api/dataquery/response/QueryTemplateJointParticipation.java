package com.paytech.platform.support.api.dataquery.response;

import lombok.Getter;
import lombok.Setter;

/** 模板版本是否参与联合查询及原因；按该版本内容与模板、数据源的当前状态推算。 */
@Getter
@Setter
public class QueryTemplateJointParticipation {

    /** 是否参与。 */
    private boolean participating;

    /**
     * 不参与原因码
     * <li>KEY_PARAMETER_NOT_REQUIRED、OTHER_REQUIRED_PARAMETER、TEMPLATE_INACTIVE、DATA_SOURCE_INACTIVE；参与时为 null。</li>
     */
    private String reasonCode;

    /** 中文说明：参与时如“参与联合查询，触发键 ORDER_ID”，不参与时为原因。 */
    private String reason;
}

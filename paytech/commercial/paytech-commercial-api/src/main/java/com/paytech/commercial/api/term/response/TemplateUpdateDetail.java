package com.paytech.commercial.api.term.response;

import lombok.Getter;
import lombok.Setter;

/** 来源模板方案已有更新的生效版本（「标准价已更新」提示）；合同价格不随之变化。 */
@Getter
@Setter
public class TemplateUpdateDetail {
    /** 模板方案当前生效的较新版本 ID */
    private String latestTemplateVersionId;

    /** 该版本的版本号 */
    private String latestVersionNo;
}

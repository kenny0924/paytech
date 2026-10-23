package com.paytech.platform.support.api.localization.response;

import lombok.Getter;
import lombok.Setter;

/** 两次发布之间一条文案的变化。 */
@Getter
@Setter
public class ReleaseTextChange {

    /** 语言代码。 */
    private String locale;

    /** 完整 key。 */
    private String keyCode;

    /** 旧发布中的文案；新增时为 null。 */
    private String before;

    /** 新发布中的文案；删除时为 null。 */
    private String after;
}

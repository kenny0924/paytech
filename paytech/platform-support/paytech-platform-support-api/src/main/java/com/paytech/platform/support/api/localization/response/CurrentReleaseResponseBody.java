package com.paytech.platform.support.api.localization.response;

import lombok.Getter;
import lombok.Setter;

/** 切换后的应用当前发布。 */
@Getter
@Setter
public class CurrentReleaseResponseBody {

    /** 应用编码。 */
    private String applicationCode;

    /** 当前发布号。 */
    private int releaseNo;
}

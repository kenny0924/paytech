package com.paytech.platform.support.api.localization.response;

import java.util.List;

import lombok.Getter;
import lombok.Setter;

/** 全部应用，按应用编码升序。 */
@Getter
@Setter
public class ApplicationListResponseBody {

    /** 应用。 */
    private List<ApplicationResponseBody> items;
}

package com.paytech.platform.support.api.localization.response;

import java.util.List;

import lombok.Getter;
import lombok.Setter;

/** 全部语言，按排序值、语言代码升序。 */
@Getter
@Setter
public class LocaleListResponseBody {

    /** 语言。 */
    private List<LocaleResponseBody> items;
}

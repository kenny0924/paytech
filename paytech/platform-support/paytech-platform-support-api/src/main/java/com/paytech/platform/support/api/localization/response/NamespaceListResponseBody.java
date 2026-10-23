package com.paytech.platform.support.api.localization.response;

import java.util.List;

import lombok.Getter;
import lombok.Setter;

/** 应用的全部命名空间，按排序值、编码升序。 */
@Getter
@Setter
public class NamespaceListResponseBody {

    /** 命名空间。 */
    private List<NamespaceResponseBody> items;
}

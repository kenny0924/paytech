package com.paytech.platform.support.api.referencedata.response;

import java.util.List;

import lombok.Getter;
import lombok.Setter;

/** 行政区查询结果。 */
@Getter
@Setter
public class CountrySubdivisionListResponseBody {

    /**
     * 行政区
     * <li>按行政区代码升序；不分页。</li>
     */
    private List<CountrySubdivisionResponseBody> items;
}

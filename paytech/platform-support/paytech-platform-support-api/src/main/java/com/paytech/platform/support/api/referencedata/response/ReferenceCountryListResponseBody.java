package com.paytech.platform.support.api.referencedata.response;

import java.util.List;

import lombok.Getter;
import lombok.Setter;

/** 国家与地区查询结果。 */
@Getter
@Setter
public class ReferenceCountryListResponseBody {

    /**
     * 国家与地区
     * <li>按国家代码升序；参考数据量小（约 250 行），不分页。</li>
     */
    private List<ReferenceCountryResponseBody> items;
}

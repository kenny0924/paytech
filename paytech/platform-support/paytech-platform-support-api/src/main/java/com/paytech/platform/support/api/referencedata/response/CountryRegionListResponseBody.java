package com.paytech.platform.support.api.referencedata.response;

import java.util.List;

import lombok.Getter;
import lombok.Setter;

/** 全部国家区域映射。 */
@Getter
@Setter
public class CountryRegionListResponseBody {

    /**
     * 国家区域映射
     * <li>按国家代码升序；参考数据量小（249 行），不分页。</li>
     */
    private List<CountryRegionResponseBody> items;
}

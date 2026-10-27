package com.paytech.platform.support.api.referencedata.response;

import java.util.List;

import lombok.Getter;
import lombok.Setter;

/** 行业查询结果。 */
@Getter
@Setter
public class IndustryListResponseBody {

    /**
     * 行业
     * <li>按大类 sortNo、细类 sortNo 排序，每个大类之后紧跟其细类；不分页。</li>
     */
    private List<IndustryResponseBody> items;
}

package com.paytech.platform.support.api.referencedata.response;

import java.util.List;

import lombok.Getter;
import lombok.Setter;

/** 币种查询结果。 */
@Getter
@Setter
public class ReferenceCurrencyListResponseBody {

    /**
     * 币种
     * <li>按币种代码升序；参考数据量小，不分页。</li>
     */
    private List<ReferenceCurrencyResponseBody> items;
}

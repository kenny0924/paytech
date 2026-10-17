package com.paytech.platform.support.api.referencedata.response;

import java.util.List;

import lombok.Getter;
import lombok.Setter;

/** 全部币种规则。 */
@Getter
@Setter
public class CurrencySpecListResponseBody {

    /**
     * 币种规则
     * <li>按币种代码升序；参考数据量小，不分页。</li>
     */
    private List<CurrencySpecResponseBody> items;
}

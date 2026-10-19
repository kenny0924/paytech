package com.paytech.ledger.api.response;

import java.util.List;

import lombok.Getter;
import lombok.Setter;

/** 币种列表。 */
@Getter
@Setter
public class CurrencyListResponseBody {
    /** 按币种代码升序的币种。 */
    private List<CurrencyResponseBody> items;
}

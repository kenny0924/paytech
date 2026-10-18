package com.paytech.ledger.api.response;

import java.util.List;

import lombok.Getter;
import lombok.Setter;

/** 按主键批量读取的科目。 */
@Getter
@Setter
public class ChartAccountListResponseBody {
    /** 按主键升序的科目；不存在的主键不返回。 */
    private List<ChartAccountResponseBody> items;
}

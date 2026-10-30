package com.paytech.platform.support.dataquery.domain;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 整体重排前后的键类型：before 按原顺序号，after 按新顺序。 */
@Getter
@AllArgsConstructor
public final class QueryKeyTypeReorder {

    /** 重排前，按原 sort_no、编码升序。 */
    private final List<QueryKeyTypeDetail> before;

    /** 重排后，按新顺序。 */
    private final List<QueryKeyTypeDetail> after;
}

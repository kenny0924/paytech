package com.paytech.operations.console.bff.service.model.dataquery.response;

import java.util.List;

import lombok.Getter;
import lombok.Setter;

/** 整体排序结果。 */
@Getter
@Setter
public class ReorderQueryKeyTypesResponseBody {

    /** 按新顺序排列的全部键类型。 */
    private List<QueryKeyTypeOrderResult> items;
}

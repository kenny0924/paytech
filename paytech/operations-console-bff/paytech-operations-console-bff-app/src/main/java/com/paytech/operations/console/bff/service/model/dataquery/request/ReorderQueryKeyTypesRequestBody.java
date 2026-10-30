package com.paytech.operations.console.bff.service.model.dataquery.request;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/** 拖动排序后保存全部键类型的新顺序。 */
@Getter
@Setter
public class ReorderQueryKeyTypesRequestBody {

    /**
     * 新顺序
     * <li>1..500 项，必须恰好包含全部 ACTIVE 与 INACTIVE 键类型且不重复，否则返回 400。</li>
     * <li>服务端在一个事务内按位置重写 sortNo = 序号 × 10（序号从 1 开始），每项版本加一。</li>
     */
    @NotEmpty
    @Size(max = 500)
    private List<@Valid @NotNull QueryKeyTypeOrderItem> items;
}

package com.paytech.platform.support.dataquery.core.enums;

import com.paytech.foundation.common.enums.CodeEnum;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 模板分页方式；分页子句由服务在语法树顶层追加，正文不能自带顶层 LIMIT。 */
@Getter
@RequiredArgsConstructor
public enum QueryPagingMode implements CodeEnum {

    /** 不分页，只取 maxRows 行，多取一行判断 hasMore。 */
    NONE("NONE"),

    /** 偏移分页：LIMIT size+1 OFFSET n，可选总数。 */
    OFFSET("OFFSET"),

    /** 游标分页：模板自写游标条件与 ORDER BY，服务追加 LIMIT size+1。 */
    CURSOR("CURSOR");

    private final String code;
}

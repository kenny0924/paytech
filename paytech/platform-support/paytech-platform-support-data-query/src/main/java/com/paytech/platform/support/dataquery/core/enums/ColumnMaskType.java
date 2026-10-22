package com.paytech.platform.support.dataquery.core.enums;

import com.paytech.foundation.common.enums.CodeEnum;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 出参列屏蔽规则（columns_json[].mask），在服务端执行。 */
@Getter
@RequiredArgsConstructor
public enum ColumnMaskType implements CodeEnum {

    /** 原样返回。 */
    NONE("NONE"),

    /** 不返回该列。 */
    HIDE("HIDE"),

    /** 全部字符替换为 *。 */
    FULL("FULL"),

    /** 保留首 keepStart 位与尾 keepEnd 位，其余替换为 *。 */
    KEEP_EDGES("KEEP_EDGES");

    private final String code;
}

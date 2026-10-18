package com.paytech.client.console.bff.domain.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/** 页面聚合中单个区域的可用性；不可用不得解释为“没有数据”或“无需材料”。 */
public enum SectionAvailability implements CodeEnum {
    /** 已从 Owner 读取到该区域。 */
    AVAILABLE("AVAILABLE"),

    /** Owner 不可用、超时或回执无效，该区域没有可信数据。 */
    UNAVAILABLE("UNAVAILABLE"),

    /** 当前状态下该区域不适用，未读取 Owner。 */
    NONE("NONE");

    private final String code;

    SectionAvailability(String code) {
        this.code = code;
    }

    @Override
    public String getCode() {
        return code;
    }

    /** 按读取结果是否为空给出可用性。 */
    public static String of(Object value) {
        return (value == null ? UNAVAILABLE : AVAILABLE).getCode();
    }
}

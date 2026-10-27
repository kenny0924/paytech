package com.paytech.client.master.domain.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/** 辖区包、目录、属性定义与采集 Schema 版本的状态。 */
public enum CatalogStatus implements CodeEnum {
    /** 草稿，未生效。 */
    DRAFT,

    /** 生效。 */
    ACTIVE,

    /** 已下线，在途引用继续有效。 */
    RETIRED;

    /** 稳定 Code，等于常量名。 */
    @Override
    public String getCode() {
        return name();
    }

    /**
     * 按 Code 解析
     *
     * @param code 待解析的 Code
     * @return 对应常量；不认识或为空时返回 null，由调用方按自身合同处理
     */
    public static CatalogStatus fromCode(String code) {
        for (CatalogStatus value : values()) {
            if (value.name().equals(code)) {
                return value;
            }
        }
        return null;
    }
}

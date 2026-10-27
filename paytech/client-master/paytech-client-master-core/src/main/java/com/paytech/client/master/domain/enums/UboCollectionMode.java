package com.paytech.client.master.domain.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/** 组织类型的受益人采集方式（rule_code=UBO_COLLECTION_MODE 的取值）。 */
public enum UboCollectionMode implements CodeEnum {
    /** 采集股东受益人与控制人。 */
    OWNERS_AND_CONTROL,

    /** 只采集控制人（上市公司等）。 */
    CONTROL_ONLY,

    /** 豁免受益人采集。 */
    EXEMPT;

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
    public static UboCollectionMode fromCode(String code) {
        for (UboCollectionMode value : values()) {
            if (value.name().equals(code)) {
                return value;
            }
        }
        return null;
    }
}

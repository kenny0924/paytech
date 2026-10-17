package com.paytech.risk.compliance.service.processor.response;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 同一只读快照下的程序修订及其材料要求。 */
@Getter
@AllArgsConstructor
public class MaterialRequirementsResponse {
    /** 精确程序 ID。 */
    private final long programId;

    /** 精确生效修订 ID。 */
    private final long programVersionId;

    /** 该修订的完整材料要求，按要求代码排序。 */
    private final List<Item> items;

    /** 单条材料要求。 */
    @Getter
    @AllArgsConstructor
    public static class Item {
        /** 稳定材料要求代码。 */
        private final String requirementCode;

        /** 受控材料类型代码。 */
        private final String documentType;

        /** 是否必需。 */
        private final boolean required;
    }
}

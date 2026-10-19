package com.paytech.commercial.domain.enums;

import java.util.Set;

import com.paytech.foundation.common.enums.CodeEnum;

/** CommercialTermVersion Status 的数据库闭合值域。 */
public enum CommercialTermVersionStatus implements CodeEnum {
    DRAFT("DRAFT"),

    /** 已提交等待审批；提交人不得审批。 */
    PENDING_APPROVAL("PENDING_APPROVAL"),

    APPROVED("APPROVED"),

    SCHEDULED("SCHEDULED"),

    ACTIVE("ACTIVE"),

    SUPERSEDED("SUPERSEDED"),

    EXPIRED("EXPIRED");

    /**
     * 按商业时点参与版本解析的状态（technique §2.3）
     * <li>SCHEDULED 已到起点但激活作业尚未切换时仍须被选中，ACTIVE / SUPERSEDED 标签与 active_slot 不是解析依据。</li>
     * <li>DRAFT、PENDING_APPROVAL、APPROVED、EXPIRED 不参与。</li>
     */
    public static final Set<CommercialTermVersionStatus> RESOLVABLE = Set.of(SCHEDULED, ACTIVE, SUPERSEDED);

    private final String code;

    CommercialTermVersionStatus(String code) {
        this.code = code;
    }

    /** 数据库使用的稳定编码。 */
    @Override
    public String getCode() {
        return code;
    }
}

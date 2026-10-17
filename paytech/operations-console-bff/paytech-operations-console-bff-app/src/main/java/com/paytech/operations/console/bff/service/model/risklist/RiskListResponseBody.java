package com.paytech.operations.console.bff.service.model.risklist;

import lombok.Getter;
import lombok.Setter;

/**
 * 风控名单。
 */
@Getter
@Setter
public class RiskListResponseBody {
    /**
     * 名单 ID
     */
    private String listId;

    /**
     * 名单编码
     */
    private String listCode;

    /**
     * 名单名称
     */
    private String listName;

    /**
     * 主体类型
     */
    private String subjectType;

    /**
     * 名单用途
     * <li>BLOCK 或 ALLOW，仅用于展示。</li>
     */
    private String purpose;

    /**
     * 是否启用
     */
    private boolean active;
}

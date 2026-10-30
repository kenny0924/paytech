package com.paytech.operations.console.bff.service.model.payout.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 收款账户 Owner 的主档摘要（client-master）
 * <li>只在主档读取成功时出现；读取失败或超时时所在位置为 null，页面显示 Owner 标识，不伪造名称。</li>
 */
@Getter
@AllArgsConstructor
public class PayoutOwnerResponseBody {
    /** Owner 类型：CLIENT、LOCAL_PARTNER */
    private final String ownerType;

    /** Owner 标识 */
    private final String ownerId;

    /** Client 编号或 Local Partner 编号 */
    private final String ownerNo;

    /**
     * 法定名称
     * <li>主档未登记时为空；核验页用它与户名原值对照。</li>
     */
    private final String legalName;

    /** 主档状态 */
    private final String status;
}

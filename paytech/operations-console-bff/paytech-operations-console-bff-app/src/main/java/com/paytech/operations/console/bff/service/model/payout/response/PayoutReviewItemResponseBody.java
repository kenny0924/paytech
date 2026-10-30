package com.paytech.operations.console.bff.service.model.payout.response;

import java.util.Map;

import com.paytech.payout.api.beneficiary.response.BeneficiaryAccountResponseBody;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 核验队列的一行：payout 账户视图（含共用提示）与 Owner 主档摘要。 */
@Getter
@AllArgsConstructor
public class PayoutReviewItemResponseBody {
    /** payout 收款账户视图（不含原值与事件） */
    private final BeneficiaryAccountResponseBody account;

    /**
     * Owner 主档摘要
     * <li>主档读取失败或超时时为 null。</li>
     */
    private final PayoutOwnerResponseBody owner;

    /**
     * 共用同一账号的其他 Owner 主档摘要
     * <li>键为 {ownerType}:{ownerId}，对应 account.sharedOwners；读取失败的 Owner 不在映射中（页面显示类型与 ID）。</li>
     */
    private final Map<String, PayoutOwnerResponseBody> sharedOwnerProfiles;
}

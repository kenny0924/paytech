package com.paytech.operations.console.bff.service.model.payout.response;

import java.util.Map;

import com.paytech.payout.api.beneficiary.response.BeneficiaryAccountResponseBody;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 收款账户核验详情：payout 账户与事件时间线、Owner 法定名（S-02）与运营操作人显示名。 */
@Getter
@AllArgsConstructor
public class PayoutBeneficiaryDetailResponseBody {
    /** payout 收款账户视图，含事件时间线与共用提示，不含原值 */
    private final BeneficiaryAccountResponseBody account;

    /**
     * Owner 主档摘要
     * <li>主档读取失败或超时时为 null，户名对照显示为不可用。</li>
     */
    private final PayoutOwnerResponseBody owner;

    /**
     * 共用同一账号的其他 Owner 主档摘要
     * <li>键为 {ownerType}:{ownerId}，对应 account.sharedOwners；读取失败的 Owner 不在映射中（页面显示类型与 ID）。</li>
     */
    private final Map<String, PayoutOwnerResponseBody> sharedOwnerProfiles;

    /**
     * 运营账号 ID 到显示名
     * <li>覆盖 Maker、Checker、运营提交人与事件中的运营操作人；Platform 查询失败时为空映射。</li>
     */
    private final Map<String, String> operatorLabels;
}

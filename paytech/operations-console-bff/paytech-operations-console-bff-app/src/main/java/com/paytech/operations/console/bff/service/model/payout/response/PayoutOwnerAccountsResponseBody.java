package com.paytech.operations.console.bff.service.model.payout.response;

import java.util.List;
import java.util.Map;

import com.paytech.payout.api.beneficiary.response.BeneficiaryAccountResponseBody;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** Owner 账户与余额页的账户部分：Owner 主档摘要、全部收款账户与运营操作人显示名。 */
@Getter
@AllArgsConstructor
public class PayoutOwnerAccountsResponseBody {
    /** Owner 类型 */
    private final String ownerType;

    /** Owner 标识 */
    private final String ownerId;

    /**
     * Owner 主档摘要
     * <li>主档读取失败、超时或不存在时为 null。</li>
     */
    private final PayoutOwnerResponseBody owner;

    /** 收款账户（payout 顺序：币种、创建时间升序） */
    private final List<BeneficiaryAccountResponseBody> items;

    /**
     * 运营账号 ID 到显示名
     * <li>覆盖本页 Maker、Checker 与运营提交人；Platform 查询失败时为空映射，页面显示账号 ID。</li>
     */
    private final Map<String, String> operatorLabels;
}

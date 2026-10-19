package com.paytech.ledger.domainservice.validator.adjustment;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.paytech.foundation.common.error.BusinessException;
import com.paytech.ledger.api.enums.LedgerErrorCode;
import com.paytech.ledger.core.dal.dataobject.ChartAccount;
import com.paytech.ledger.core.dal.dataobject.LedgerAccount;
import com.paytech.ledger.domainservice.validator.account.AccountIdentityRule;

/**
 * 人工调整的 Owner 控制科目禁令（技术方案 2.1）：调整行的账户科目属于 Owner 控制科目集合即拒绝
 * <li>控制科目（CLIENT_PAYABLE、CLIENT_SECURITY_DEPOSIT、CLIENT_RECOVERY_RECEIVABLE、PARTNER_PAYABLE、PARTNER_RECOVERY_RECEIVABLE）
 * 的余额必须等于 account 明细账合计，只能由 account 入账批次生成；影响 Owner 余额的调整必须经 billing 登记 MANUAL_ADJUSTMENT 经济事实。</li>
 * <li>只读入参，不访问数据库；提交与批准两个入口共用，错误码不变。</li>
 */
public final class OwnerAccountAdjustmentRule {

    private OwnerAccountAdjustmentRule() {
    }

    /**
     * 拒绝涉及 Owner 控制科目的人工调整
     *
     * @param adjustmentRef 调整标识（新提交时为账簿描述），用于错误上下文
     * @param accounts 调整行引用的全部账户
     * @param charts 这些账户引用的全部科目
     */
    public static void checkNoControlAccounts(String adjustmentRef, Collection<LedgerAccount> accounts,
        Collection<ChartAccount> charts) {
        Map<Long, String> codes = charts.stream().collect(Collectors.toMap(ChartAccount::getId,
            ChartAccount::getAccountCode, (first, second) -> first));
        List<Long> controls = accounts.stream()
            .filter(account -> AccountIdentityRule.isControlAccount(codes.get(account.getChartAccountId())))
            .map(LedgerAccount::getId).sorted().toList();
        if (!controls.isEmpty()) {
            throw new BusinessException(LedgerErrorCode.OWNER_ACCOUNT_ADJUSTMENT_FORBIDDEN,
                "人工调整不得涉及 Client 或 Local Partner 的控制科目，请经计费登记 MANUAL_ADJUSTMENT 经济事实: " + adjustmentRef
                    + ", 账户 " + controls);
        }
    }
}

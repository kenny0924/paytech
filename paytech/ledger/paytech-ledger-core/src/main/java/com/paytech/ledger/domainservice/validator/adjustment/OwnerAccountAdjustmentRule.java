package com.paytech.ledger.domainservice.validator.adjustment;

import java.util.Collection;
import java.util.List;
import java.util.Set;

import com.paytech.account.api.enums.OwnerType;
import com.paytech.foundation.common.error.BusinessException;
import com.paytech.ledger.api.enums.LedgerErrorCode;
import com.paytech.ledger.core.dal.dataobject.LedgerAccount;

/**
 * 人工调整的 Owner 账户禁令（design/04 §4 第 3 行）：调整行涉及 Client 或 Local Partner 账户即拒绝
 * <li>影响 Owner 余额的调整必须经 billing 登记 MANUAL_ADJUSTMENT 经济事实，由 ledger 与 account 各自消费；纯科目重分类保留在 ledger，只写分录。</li>
 * <li>只读入参，不访问数据库；提交与批准两个入口共用。</li>
 */
public final class OwnerAccountAdjustmentRule {
    /** 余额由 account 服务维护、不得经 ledger 直接调整的核算对象类型。 */
    static final Set<OwnerType> OWNER_TYPES = Set.of(OwnerType.CLIENT, OwnerType.LOCAL_PARTNER);

    private OwnerAccountAdjustmentRule() {
    }

    /**
     * 拒绝涉及 Owner 账户的人工调整
     *
     * @param adjustmentRef 调整标识（新提交时为账簿与期间描述），用于错误上下文
     * @param accounts 调整行引用的全部账户
     */
    public static void checkNoOwnerAccounts(String adjustmentRef, Collection<LedgerAccount> accounts) {
        List<Long> owners = accounts.stream().filter(account -> OWNER_TYPES.contains(account.getOwnerType()))
            .map(LedgerAccount::getId).sorted().toList();
        if (!owners.isEmpty()) {
            throw new BusinessException(LedgerErrorCode.OWNER_ACCOUNT_ADJUSTMENT_FORBIDDEN,
                "人工调整不得涉及 Client 或 Local Partner 账户，请经计费登记 MANUAL_ADJUSTMENT 经济事实: " + adjustmentRef
                    + ", 账户 " + owners);
        }
    }
}

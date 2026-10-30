package com.paytech.payout.integration;

import java.util.List;

import com.paytech.payout.api.enums.BeneficiaryOwnerType;
import com.paytech.payout.domain.OwnerBalancePosition;

/**
 * account Owner 余额列表的出站端口（technique 8.2，S-04）
 * <li>只读，事务外调用；Owner 在 account 无账户时返回空列表；account 不可用时上抛 503。</li>
 */
public interface OwnerBalanceClient {
    /**
     * 列出 Owner 全部 OE × 币种的余额
     *
     * @param ownerType Owner 类型
     * @param ownerId Owner 标识
     * @return 余额位置
     */
    List<OwnerBalancePosition> listPositions(BeneficiaryOwnerType ownerType, Long ownerId);
}

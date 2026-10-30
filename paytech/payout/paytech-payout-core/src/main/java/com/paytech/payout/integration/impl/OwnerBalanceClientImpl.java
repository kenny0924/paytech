package com.paytech.payout.integration.impl;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.stereotype.Component;

import com.paytech.account.api.BalanceQueryApi;
import com.paytech.account.api.response.ClientBalanceResponseBody;
import com.paytech.account.api.response.PartnerBalanceResponseBody;
import com.paytech.foundation.common.error.ErrorException;
import com.paytech.foundation.money.Money;
import com.paytech.payout.api.enums.BeneficiaryOwnerType;
import com.paytech.payout.domain.OwnerBalancePosition;
import com.paytech.payout.integration.OwnerBalanceClient;

import lombok.AllArgsConstructor;

/** 经 account 余额列表接口（BalanceQueryApi positions）读取 Owner 余额，并转换为本服务领域对象；远端 DTO 不越过本边界。 */
@Component
@AllArgsConstructor
public class OwnerBalanceClientImpl implements OwnerBalanceClient {
    private final BalanceQueryApi balanceQueryApi;

    /** 按 Owner 类型选择 Client 或 Partner 列表接口。 */
    @Override
    public List<OwnerBalancePosition> listPositions(BeneficiaryOwnerType ownerType, Long ownerId) {
        if (ownerType == BeneficiaryOwnerType.CLIENT) {
            List<ClientBalanceResponseBody> bodies = balanceQueryApi.listClientPositions(ownerId.toString());
            return bodies == null ? List.of() : bodies.stream().map(OwnerBalanceClientImpl::toPosition).toList();
        }
        List<PartnerBalanceResponseBody> bodies = balanceQueryApi.listPartnerPositions(ownerId.toString());
        return bodies == null ? List.of() : bodies.stream().map(OwnerBalanceClientImpl::toPosition).toList();
    }

    /** Client 余额转领域余额；必需字段缺失视为上游响应不符合合同。 */
    static OwnerBalancePosition toPosition(ClientBalanceResponseBody body) {
        requireFields(body.getEntityNo(), body.getCurrency(), body.getAvailableAmount());
        return new OwnerBalancePosition(body.getEntityNo(), body.getCurrency(), amount(body.getPendingAmount()),
            amount(body.getAvailableAmount()), amount(body.getLockedAmount()), nullableAmount(body.getSecurityDepositAmount()),
            amount(body.getRecoveryReceivableAmount()));
    }

    /** Local Partner 余额转领域余额（无保证金）。 */
    static OwnerBalancePosition toPosition(PartnerBalanceResponseBody body) {
        requireFields(body.getEntityNo(), body.getCurrency(), body.getAvailableAmount());
        return new OwnerBalancePosition(body.getEntityNo(), body.getCurrency(), amount(body.getPendingAmount()),
            amount(body.getAvailableAmount()), amount(body.getLockedAmount()), null, amount(body.getRecoveryReceivableAmount()));
    }

    /** 必需字段检查。 */
    static void requireFields(String entityNo, String currency, Money availableAmount) {
        if (entityNo == null || currency == null || availableAmount == null) {
            throw new ErrorException("account 余额列表响应缺少必需字段");
        }
    }

    /** 缺省为零的金额。 */
    static BigDecimal amount(Money money) {
        return money == null ? BigDecimal.ZERO : money.getAmount();
    }

    /** 可空金额。 */
    static BigDecimal nullableAmount(Money money) {
        return money == null ? null : money.getAmount();
    }
}

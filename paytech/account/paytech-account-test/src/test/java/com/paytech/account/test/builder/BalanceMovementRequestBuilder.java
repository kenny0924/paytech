package com.paytech.account.test.builder;

import java.util.UUID;

import com.paytech.account.api.enums.AccountEntityType;
import com.paytech.account.api.enums.BalanceBucket;
import com.paytech.account.api.enums.MovementSourceType;
import com.paytech.account.api.enums.OwnerAccountType;
import com.paytech.account.api.request.BalanceMovementRequestBody;
import com.paytech.foundation.money.Money;

/**
 * 余额桶移动请求的合法模板：AE001/USD 下 Client 1001 的 CLIENT_PAYABLE 出款锁定（AVAILABLE → LOCKED），来源引用每次随机
 * <li>只构造对象，不访问外部资源；允许设置非法组合与非法值作为负例输入，不做修正。</li>
 */
public final class BalanceMovementRequestBuilder {
    private final BalanceMovementRequestBody body = new BalanceMovementRequestBody();

    private BalanceMovementRequestBuilder() {
    }

    /** 合法模板。 */
    public static BalanceMovementRequestBuilder template() {
        BalanceMovementRequestBuilder builder = new BalanceMovementRequestBuilder();
        builder.body.setEntityType(AccountEntityType.OPERATING_ENTITY.getCode());
        builder.body.setEntityId("AE001");
        builder.body.setCurrency("USD");
        builder.body.setSourceRef("payout-" + UUID.randomUUID());
        builder.body.setOperatorId("90001");
        return builder.source(MovementSourceType.PAYOUT_LOCK).account(OwnerAccountType.CLIENT_PAYABLE, "1001")
            .amount("30");
    }

    /** 来源类型，同时把转出 / 转入桶设为该类型的固定方向。 */
    public BalanceMovementRequestBuilder source(MovementSourceType sourceType) {
        body.setSourceType(sourceType.getCode());
        return buckets(sourceType.getFromBucket(), sourceType.getToBucket());
    }

    /** 直接设置来源类型 Code（负例）。 */
    public BalanceMovementRequestBuilder sourceCode(String sourceType) {
        body.setSourceType(sourceType);
        return this;
    }

    /** 转出与转入桶。 */
    public BalanceMovementRequestBuilder buckets(BalanceBucket from, BalanceBucket to) {
        body.setFromBucket(from.getCode());
        body.setToBucket(to.getCode());
        return this;
    }

    /** 账户类型与核算对象。 */
    public BalanceMovementRequestBuilder account(OwnerAccountType type, String ownerId) {
        body.setAccountCode(type.getCode());
        body.setOwnerType(type.getOwnerType().getCode());
        body.setOwnerId(ownerId);
        return this;
    }

    /** 直接设置核算对象类型 Code（负例）。 */
    public BalanceMovementRequestBuilder ownerType(String ownerType) {
        body.setOwnerType(ownerType);
        return this;
    }

    /** 主体类型与标识。 */
    public BalanceMovementRequestBuilder entity(AccountEntityType type, String entityId) {
        body.setEntityType(type.getCode());
        body.setEntityId(entityId);
        return this;
    }

    /** 请求币种下的金额。 */
    public BalanceMovementRequestBuilder amount(String amount) {
        body.setAmount(Money.of(body.getCurrency(), amount));
        return this;
    }

    /** 指定币种的金额（币种不一致负例）。 */
    public BalanceMovementRequestBuilder amount(String currency, String amount) {
        body.setAmount(Money.of(currency, amount));
        return this;
    }

    /** 来源引用。 */
    public BalanceMovementRequestBuilder sourceRef(String sourceRef) {
        body.setSourceRef(sourceRef);
        return this;
    }

    /** 返回构造好的请求体。 */
    public BalanceMovementRequestBody build() {
        return body;
    }
}

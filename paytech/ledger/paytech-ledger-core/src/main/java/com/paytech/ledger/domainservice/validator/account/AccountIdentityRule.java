package com.paytech.ledger.domainservice.validator.account;

import java.util.Arrays;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.paytech.account.api.enums.BalanceBucket;
import com.paytech.account.api.enums.OwnerAccountType;
import com.paytech.account.api.enums.OwnerType;
import com.paytech.foundation.common.error.BizCheckFailException;
import com.paytech.foundation.common.error.InvalidParameterException;
import com.paytech.ledger.api.enums.LedgerOwnerType;
import com.paytech.ledger.core.dal.dataobject.LedgerBook;

/**
 * 总账账户身份矩阵与主体标识格式（2026-10-18 决定第 3 条：account 明细账、ledger 控制科目）
 * <li>Owner 控制科目（account 目录中 Owner 为 Client 或 Local Partner 的科目）每账簿每科目只有一个控制账户：Owner 类型为
 * OPERATING_ENTITY 或 FINANCE_ENTITY 且 Owner 标识等于账簿主体，余额桶 NONE；明细在 account 的 Owner 账户。</li>
 * <li>其余科目只允许平台类 Owner（经营主体、财务主体、渠道账户、银行账户、税务机关），余额桶一律 NONE；BANK_CASH 仍为 CASH_ACCOUNT。</li>
 * <li>OE 使用 entityNo（两位国家码 + 001—999），其余数字身份使用规范十进制正整数字符串。</li>
 */
public final class AccountIdentityRule {
    /** 账簿与日历主体类型中 Operating Entity 的稳定代码。 */
    public static final String OPERATING_ENTITY_CODE = "OPERATING_ENTITY";

    /** Owner 控制科目：account 目录中 Owner 为 Client 或 Local Partner 的科目代码。 */
    public static final Set<String> CONTROL_ACCOUNT_CODES = Arrays.stream(OwnerAccountType.values())
        .filter(type -> type.getOwnerType() == OwnerType.CLIENT || type.getOwnerType() == OwnerType.LOCAL_PARTNER)
        .map(OwnerAccountType::getCode).collect(Collectors.toUnmodifiableSet());

    private static final Pattern ENTITY_NO = Pattern.compile("[A-Z]{2}(00[1-9]|0[1-9][0-9]|[1-9][0-9]{2})");
    private static final Pattern DECIMAL_ID = Pattern.compile("[1-9][0-9]{0,18}");
    private static final Set<LedgerOwnerType> BOOK_ENTITY_OWNERS =
        Set.of(LedgerOwnerType.OPERATING_ENTITY, LedgerOwnerType.FINANCE_ENTITY);

    private AccountIdentityRule() {
    }

    /** 科目是否为 Owner 控制科目；科目代码为空时不是。 */
    public static boolean isControlAccount(String accountCode) {
        return accountCode != null && CONTROL_ACCOUNT_CODES.contains(accountCode);
    }

    /**
     * 校验科目、Owner 类型与余额桶组合是否属于账户身份矩阵（不含账簿主体比对）
     * <li>规则版本与过账使用：带分项提示的业务拒绝。</li>
     */
    public static void checkAccountIdentity(String accountCode, LedgerOwnerType ownerType, BalanceBucket bucket) {
        if (!ownerAllowed(accountCode, ownerType)) {
            throw new BizCheckFailException("科目不允许该核算对象: 科目 " + accountCode + ", Owner 类型 " + ownerType.getCode());
        }
        if (bucket != BalanceBucket.NONE) {
            throw new BizCheckFailException("总账账户余额桶只能为 NONE: 科目 " + accountCode + ", 余额桶 " + bucket.getCode());
        }
    }

    /**
     * 判断账户身份是否被允许（创建账户以稳定错误码拒绝）
     * <li>控制科目还要求 Owner 类型、标识与账簿主体一致。</li>
     *
     * @param book 账户所在账簿
     * @return 核算对象、余额桶与账簿主体均符合矩阵时为 true
     */
    public static boolean isAccountIdentityAllowed(String accountCode, LedgerOwnerType ownerType, String ownerId,
        BalanceBucket bucket, LedgerBook book) {
        if (!ownerAllowed(accountCode, ownerType) || bucket != BalanceBucket.NONE) {
            return false;
        }
        return !isControlAccount(accountCode) || isBookEntity(book, ownerType, ownerId);
    }

    /** 控制科目只允许账簿主体类 Owner，其余科目允许全部平台类 Owner。 */
    static boolean ownerAllowed(String accountCode, LedgerOwnerType ownerType) {
        return !isControlAccount(accountCode) || BOOK_ENTITY_OWNERS.contains(ownerType);
    }

    /** Owner 是否就是账簿主体本身。 */
    static boolean isBookEntity(LedgerBook book, LedgerOwnerType ownerType, String ownerId) {
        return ownerType.getCode().equals(book.getEntityType().getCode()) && book.getEntityId().equals(ownerId);
    }

    /** 账簿主体对应的 Owner 类型：控制账户与 BOOK_ENTITY 规则行的核算对象。 */
    public static LedgerOwnerType bookOwnerType(LedgerBook book) {
        return LedgerOwnerType.valueOf(book.getEntityType().getCode());
    }

    /** 校验 Owner 标识格式：OE 为 entityNo，其他为规范十进制正整数。 */
    public static void checkOwnerId(LedgerOwnerType ownerType, String ownerId) {
        Pattern pattern = ownerType == LedgerOwnerType.OPERATING_ENTITY ? ENTITY_NO : DECIMAL_ID;
        if (!pattern.matcher(ownerId).matches()) {
            throw new InvalidParameterException("Owner 标识格式非法: " + ownerType.getCode() + "/" + ownerId);
        }
    }

    /**
     * 校验账簿或日历主体标识格式：OE 为 entityNo，Finance Entity 为本地主键十进制字符串
     * <li>主体类型以稳定代码传入（ledger {@code LedgerEntityType} 的代码）。</li>
     *
     * @param entityTypeCode 主体类型代码：OPERATING_ENTITY 或 FINANCE_ENTITY
     * @param entityId 主体标识
     */
    public static void checkEntityId(String entityTypeCode, String entityId) {
        Pattern pattern = OPERATING_ENTITY_CODE.equals(entityTypeCode) ? ENTITY_NO : DECIMAL_ID;
        if (!pattern.matcher(entityId).matches()) {
            throw new InvalidParameterException("主体标识格式非法: " + entityTypeCode + "/" + entityId);
        }
    }
}

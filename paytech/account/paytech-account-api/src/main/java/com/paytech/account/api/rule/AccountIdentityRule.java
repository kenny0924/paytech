package com.paytech.account.api.rule;

import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import com.paytech.account.api.constant.StandardAccountCodes;
import com.paytech.account.api.enums.BalanceBucket;
import com.paytech.account.api.enums.OwnerType;
import com.paytech.foundation.common.error.BizCheckFailException;
import com.paytech.foundation.common.error.InvalidParameterException;

/**
 * 统一账户身份矩阵与主体标识格式
 * <li>Client/Partner 专属科目限定 Owner 类型与余额桶；其他科目只允许平台侧 Owner 且余额桶为 NONE。</li>
 * <li>OE 使用 entityNo（两位国家码 + 001—999），其余数字身份使用规范十进制正整数字符串。</li>
 * <li>过渡位置（design/03 §3、任务卡 07 第 3.3 项）：ledger 解析科目账户与 account 查询余额都需要本规则，规则接口加 DTO 注入无法在不依赖对方 core 的前提下装配，
 * 因此本步以纯静态规则放在 account-api；第 11b 步账户身份收回 account-core / ledger 后移出 api。</li>
 */
public final class AccountIdentityRule {
    /** 账簿与日历主体类型中 Operating Entity 的稳定代码。 */
    public static final String OPERATING_ENTITY_CODE = "OPERATING_ENTITY";

    private static final Pattern ENTITY_NO = Pattern.compile("[A-Z]{2}(00[1-9]|0[1-9][0-9]|[1-9][0-9]{2})");
    private static final Pattern DECIMAL_ID = Pattern.compile("[1-9][0-9]{0,18}");
    private static final Set<BalanceBucket> PAYABLE_BUCKETS =
        Set.of(BalanceBucket.PENDING, BalanceBucket.AVAILABLE, BalanceBucket.LOCKED);
    private static final Set<OwnerType> PLATFORM_OWNERS = Set.of(OwnerType.OPERATING_ENTITY, OwnerType.FINANCE_ENTITY,
        OwnerType.CHANNEL_ACCOUNT, OwnerType.CASH_ACCOUNT, OwnerType.TAX_AUTHORITY);
    private static final Map<String, OwnerType> OWNER_SPECIFIC_CODES = Map.of(
        StandardAccountCodes.CLIENT_PAYABLE, OwnerType.CLIENT,
        StandardAccountCodes.CLIENT_SECURITY_DEPOSIT, OwnerType.CLIENT,
        StandardAccountCodes.CLIENT_RECOVERY_RECEIVABLE, OwnerType.CLIENT,
        StandardAccountCodes.PARTNER_PAYABLE, OwnerType.LOCAL_PARTNER,
        StandardAccountCodes.PARTNER_RECOVERY_RECEIVABLE, OwnerType.LOCAL_PARTNER);

    private AccountIdentityRule() {
    }

    /** 校验科目、Owner 类型与余额桶组合是否属于账户身份矩阵。 */
    public static void checkAccountIdentity(String accountCode, OwnerType ownerType, BalanceBucket bucket) {
        OwnerType requiredOwner = OWNER_SPECIFIC_CODES.get(accountCode);
        boolean ownerAllowed = requiredOwner == null ? PLATFORM_OWNERS.contains(ownerType) : requiredOwner == ownerType;
        if (!ownerAllowed) {
            throw new BizCheckFailException("科目不允许该核算对象: 科目 " + accountCode + ", Owner 类型 " + ownerType.getCode());
        }
        if (!allowedBuckets(accountCode).contains(bucket)) {
            throw new BizCheckFailException("科目不允许该余额桶: 科目 " + accountCode + ", 余额桶 " + bucket.getCode());
        }
    }

    /** 校验 Owner 标识格式：OE 为 entityNo，其他为规范十进制正整数。 */
    public static void checkOwnerId(OwnerType ownerType, String ownerId) {
        Pattern pattern = ownerType == OwnerType.OPERATING_ENTITY ? ENTITY_NO : DECIMAL_ID;
        if (!pattern.matcher(ownerId).matches()) {
            throw new InvalidParameterException("Owner 标识格式非法: " + ownerType.getCode() + "/" + ownerId);
        }
    }

    /**
     * 校验账簿或日历主体标识格式：OE 为 entityNo，Finance Entity 为本地主键十进制字符串
     * <li>主体类型以稳定代码传入（ledger {@code LedgerEntityType} 的代码），api 不依赖 ledger 枚举。</li>
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

    /** 应付控制科目使用三个余额桶，其他科目只使用 NONE。 */
    static Set<BalanceBucket> allowedBuckets(String accountCode) {
        if (StandardAccountCodes.CLIENT_PAYABLE.equals(accountCode)
            || StandardAccountCodes.PARTNER_PAYABLE.equals(accountCode)) {
            return PAYABLE_BUCKETS;
        }
        return Set.of(BalanceBucket.NONE);
    }
}

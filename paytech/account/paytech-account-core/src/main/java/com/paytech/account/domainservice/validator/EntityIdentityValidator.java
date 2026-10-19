package com.paytech.account.domainservice.validator;

import java.util.regex.Pattern;

import com.paytech.account.api.enums.AccountEntityType;
import com.paytech.foundation.common.error.InvalidParameterException;

/**
 * 主体类型与主体标识的对应关系：OPERATING_ENTITY 为 entityNo（两位大写字母加三位数字），FINANCE_ENTITY 为正十进制主键
 * <li>接口正则只保证 [A-Z0-9]{1,19}；多个查询与命令入口共用本规则，在锁和查询之前拒绝。</li>
 */
public final class EntityIdentityValidator {
    private static final Pattern ENTITY_NO = Pattern.compile("[A-Z]{2}[0-9]{3}");

    private static final Pattern DECIMAL_ID = Pattern.compile("[1-9][0-9]{0,18}");

    private EntityIdentityValidator() {
    }

    /**
     * 校验主体标识与主体类型一致
     *
     * @param entityType 主体类型
     * @param entityId 主体标识
     * @throws InvalidParameterException 不一致
     */
    public static void requireConsistent(AccountEntityType entityType, String entityId) {
        Pattern pattern = entityType == AccountEntityType.OPERATING_ENTITY ? ENTITY_NO : DECIMAL_ID;
        if (entityId == null || !pattern.matcher(entityId).matches()) {
            throw new InvalidParameterException("主体标识与主体类型不符: " + entityType.getCode());
        }
    }
}

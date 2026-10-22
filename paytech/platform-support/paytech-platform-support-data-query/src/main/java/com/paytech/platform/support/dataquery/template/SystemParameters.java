package com.paytech.platform.support.dataquery.template;

import java.util.Map;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 服务端注入的系统参数；调用方传入以 __ 开头的参数一律拒绝。 */
@Getter
@AllArgsConstructor
public final class SystemParameters {

    /** 操作人参数名，模板写 __operator.accountId。 */
    public static final String OPERATOR = "__operator";

    /** 操作人参数的字段名。 */
    public static final String OPERATOR_ACCOUNT_ID = "accountId";

    /** 游标参数名，模板写 __cursor.列名。 */
    public static final String CURSOR = "__cursor";

    /** 操作人账号 ID（t_user_account.account_id）；没有时为 null。 */
    private final Long operatorAccountId;

    /** 上一页最后一行的游标列值；首页为 null。 */
    private final Map<String, Object> cursor;
}

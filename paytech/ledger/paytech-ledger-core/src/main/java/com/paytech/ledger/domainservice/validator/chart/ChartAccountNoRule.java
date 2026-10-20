package com.paytech.ledger.domainservice.validator.chart;

import java.util.Map;
import java.util.regex.Pattern;

import com.paytech.foundation.common.error.FailException;
import com.paytech.ledger.api.enums.LedgerSetupErrorCode;
import com.paytech.ledger.core.enums.AccountClass;

/**
 * 科目编号规则（2026-10-19 科目分级决定第 1 条）
 * <li>4-2-2-2：一级 4 位、每下一级加 2 位，最多 4 级；级次由编号长度推出，不另存。</li>
 * <li>一级首位按会计要素：1 资产、2 负债、3 权益、4 收入、5 费用；下级编号以上级编号开头。</li>
 */
public final class ChartAccountNoRule {
    /** 一级科目编号位数。 */
    public static final int TOP_LEVEL_LENGTH = 4;

    /** 最大级次。 */
    public static final int MAX_LEVEL = 4;

    /** 每下一级增加的位数。 */
    public static final int LEVEL_STEP = 2;

    private static final Pattern ACCOUNT_NO = Pattern.compile("[1-9][0-9]{3}([0-9]{2}){0,3}");

    private static final Map<AccountClass, Character> CLASS_DIGITS = Map.of(AccountClass.ASSET, '1',
        AccountClass.LIABILITY, '2', AccountClass.EQUITY, '3', AccountClass.REVENUE, '4', AccountClass.EXPENSE, '5');

    private ChartAccountNoRule() {
    }

    /**
     * 校验科目编号的格式、类别首位、上级前缀与长度
     *
     * @param accountNo 科目编号
     * @param accountClass 科目类别
     * @param parentAccountNo 上级科目编号；一级科目为空
     * @throws FailException 不符合时以 CHART_ACCOUNT_NO_INVALID 拒绝
     */
    public static void check(String accountNo, AccountClass accountClass, String parentAccountNo) {
        if (!isValid(accountNo, accountClass, parentAccountNo)) {
            throw new FailException(LedgerSetupErrorCode.CHART_ACCOUNT_NO_INVALID,
                "科目编号不符合分级规则: " + accountNo + ", 类别 " + accountClass.getCode() + ", 上级 " + parentAccountNo);
        }
    }

    /** 编号是否符合 4-2-2-2、类别首位，以及（有上级时）上级前缀与长度。 */
    static boolean isValid(String accountNo, AccountClass accountClass, String parentAccountNo) {
        if (accountNo == null || !ACCOUNT_NO.matcher(accountNo).matches()) {
            return false;
        }
        if (accountNo.charAt(0) != CLASS_DIGITS.get(accountClass)) {
            return false;
        }
        if (parentAccountNo == null) {
            return accountNo.length() == TOP_LEVEL_LENGTH;
        }
        return accountNo.length() == parentAccountNo.length() + LEVEL_STEP && accountNo.startsWith(parentAccountNo);
    }

    /** 由编号长度推出的级次：4 位为 1 级，每加 2 位加 1 级。 */
    public static int level(String accountNo) {
        return (accountNo.length() - TOP_LEVEL_LENGTH) / LEVEL_STEP + 1;
    }
}

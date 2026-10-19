package com.paytech.ledger.api.enums;

import com.paytech.foundation.common.enums.CodeMessageEnum;

/**
 * 账务基础资料与账簿命令/查询中调用方需要按错误码区分处理的业务拒绝（HTTP 400）
 * <li>Owner 以 {@code new FailException(code)} 抛出，Problem {@code code} 为枚举 Code、{@code detail} 为默认消息。</li>
 * <li>消费者：Operations BFF 在 {@code HttpServiceClientFactory} 显式注册本枚举并原样返回 400；Operations Web 按 Code 把拒绝映射到对应表单字段或页面提示。</li>
 * <li>未列出的业务拒绝仍使用公共 BUSINESS_CHECK_FAILED，参数错误使用公共 INVALID_REQUEST。</li>
 */
public enum LedgerSetupErrorCode implements CodeMessageEnum {
    /** 财务主体代码或法律 Party 唯一键冲突 */
    FINANCE_ENTITY_ALREADY_EXISTS("FINANCE_ENTITY_ALREADY_EXISTS", "财务主体代码或法律 Party 已被使用"),

    /** 新建账簿或财务日历时引用的财务主体不存在或非 ACTIVE */
    FINANCE_ENTITY_UNAVAILABLE("FINANCE_ENTITY_UNAVAILABLE", "财务主体不存在或已关闭"),

    /** 同一主体重复创建财务日历 */
    FISCAL_CALENDAR_ALREADY_EXISTS("FISCAL_CALENDAR_ALREADY_EXISTS", "该主体已有财务日历"),

    /** 创建或查询会计期间时财务日历不存在 */
    FISCAL_CALENDAR_NOT_FOUND("FISCAL_CALENDAR_NOT_FOUND", "财务日历不存在"),

    /** 新期间日期与同日历其他期间重叠 */
    ACCOUNTING_PERIOD_OVERLAP("ACCOUNTING_PERIOD_OVERLAP", "期间日期与同日历其他期间重叠"),

    /** 同一日历同一财年期号重复 */
    ACCOUNTING_PERIOD_NO_DUPLICATE("ACCOUNTING_PERIOD_NO_DUPLICATE", "同一财年期号已存在"),

    /** 开放的会计期间不存在 */
    ACCOUNTING_PERIOD_NOT_FOUND("ACCOUNTING_PERIOD_NOT_FOUND", "会计期间不存在"),

    /** 开放非 FUTURE 且非 OPEN 的会计期间 */
    ACCOUNTING_PERIOD_NOT_FUTURE("ACCOUNTING_PERIOD_NOT_FUTURE", "只有 FUTURE 期间可以开放"),

    /** 同主体同币种重复创建账簿 */
    LEDGER_BOOK_ALREADY_EXISTS("LEDGER_BOOK_ALREADY_EXISTS", "同主体同币种已有账簿"),

    /** 新建账簿的币种不存在或非 ACTIVE */
    CURRENCY_NOT_ACTIVE("CURRENCY_NOT_ACTIVE", "币种未启用"),

    /** 新建账簿的科目表不存在或非 ACTIVE，或查询科目时科目表不存在 */
    CHART_NOT_ACTIVE("CHART_NOT_ACTIVE", "科目表不存在或未启用"),

    /** 创建账户或查询账簿内数据时账簿不存在 */
    LEDGER_BOOK_NOT_FOUND("LEDGER_BOOK_NOT_FOUND", "账簿不存在"),

    /** 创建账户的科目不存在或不允许过账 */
    CHART_ACCOUNT_NOT_POSTABLE("CHART_ACCOUNT_NOT_POSTABLE", "科目不存在或不可过账"),

    /** 创建账户的核算对象类型或余额桶不符合账户身份矩阵 */
    ACCOUNT_IDENTITY_INVALID("ACCOUNT_IDENTITY_INVALID", "核算对象或余额桶不符合账户身份矩阵"),

    /** 同账簿同科目同核算对象同余额桶的账户已存在 */
    ACCOUNT_ALREADY_EXISTS("ACCOUNT_ALREADY_EXISTS", "账户已存在");

    private final String code;

    private final String message;

    /** 固定 Code 与安全默认消息。 */
    LedgerSetupErrorCode(String code, String message) {
        this.code = code;
        this.message = message;
    }

    /** 稳定错误 Code。 */
    @Override
    public String getCode() {
        return code;
    }

    /** 可外显的默认消息。 */
    @Override
    public String getMessage() {
        return message;
    }
}

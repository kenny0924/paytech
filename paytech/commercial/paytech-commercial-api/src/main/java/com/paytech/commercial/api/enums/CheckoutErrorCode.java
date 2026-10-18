package com.paytech.commercial.api.enums;

import com.paytech.foundation.common.enums.CodeMessageEnum;

/** Commerce 需要区分处理的 CCT 查询错误。 */
public enum CheckoutErrorCode implements CodeMessageEnum {
    CLIENT_NOT_FOUND("CLIENT_NOT_FOUND", "Client 不存在或无权访问"),

    INVALID_CHECKOUT_QUERY("INVALID_CHECKOUT_QUERY", "Checkout 查询参数不符合约定"),

    CLIENT_NOT_ELIGIBLE("CLIENT_NOT_ELIGIBLE", "Client 不具备当前交易资格"),

    NO_ELIGIBLE_ENTITY("NO_ELIGIBLE_ENTITY", "没有可用经营主体"),

    CCT_CONTEXT_AMBIGUOUS("CCT_CONTEXT_AMBIGUOUS", "商业配置存在冲突"),

    CCT_CONTEXT_CHANGED("CCT_CONTEXT_CHANGED", "查询期间商业配置发生变化"),

    OWNER_UNAVAILABLE("OWNER_UNAVAILABLE", "必要业务依赖暂不可用");
    private final String code;
    private final String message;
    CheckoutErrorCode(String code, String message) {
        this.code = code;
        this.message = message;
    }
    @Override
    public String getCode() {
        return code;
    }
    @Override
    public String getMessage() {
        return message;
    }
}

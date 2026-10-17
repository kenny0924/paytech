package com.paytech.client.master.api.enums;

import com.paytech.foundation.common.enums.CodeMessageEnum;

/**
 * Checkout 候选查询需要由 commercial 区分处理的错误。
 * <li>commercial 把前三项映射为原 Checkout 合同的同名业务错误，其余一律失败关闭为 OWNER_UNAVAILABLE。</li>
 */
public enum CheckoutCandidateErrorCode implements CodeMessageEnum {
    CLIENT_NOT_FOUND("CLIENT_NOT_FOUND", "Client 不存在或无权访问"),

    CLIENT_NOT_ELIGIBLE("CLIENT_NOT_ELIGIBLE", "Client 不具备当前交易资格"),

    CANDIDATE_CONTEXT_AMBIGUOUS("CANDIDATE_CONTEXT_AMBIGUOUS", "客户主档配置存在冲突"),

    INVALID_CANDIDATE_QUERY("INVALID_CANDIDATE_QUERY", "候选查询参数不符合约定"),

    CANDIDATE_SOURCE_UNAVAILABLE("CANDIDATE_SOURCE_UNAVAILABLE", "客户主档数据暂不可用");

    private final String code;

    private final String message;

    /** 固定 Code 与安全默认消息。 */
    CheckoutCandidateErrorCode(String code, String message) {
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

package com.paytech.platform.support.api.enums;

import com.paytech.foundation.common.enums.CodeMessageEnum;

/**
 * 数据查询执行与试运行中调用方需要区别处理的失败原因。
 * <p>
 * 消费者：operations-console-bff 原样转发，operations-console-web 按 Code 提示：超时与结果过大提示缩小查询范围，
 * 数据源不可用提示稍后重试或联系管理员。其余失败使用公共 {@code BUSINESS_CHECK_FAILED} / {@code INVALID_REQUEST}
 * / {@code VALIDATION_ERROR}。
 */
public enum DataQueryErrorCode implements CodeMessageEnum {

    /** 查询超过 min(模板超时, 系统上限) 被取消，HTTP 400。 */
    DATA_QUERY_TIMEOUT("DATA_QUERY_TIMEOUT", "查询超时，请缩小查询范围"),

    /** 结果累计字节超过 data-query.limits.max-response-bytes，HTTP 400。 */
    DATA_QUERY_RESULT_TOO_LARGE("DATA_QUERY_RESULT_TOO_LARGE", "查询结果过大，请缩小查询范围或减少返回列"),

    /** 无法从连接池取得连接或连接失败，HTTP 503。 */
    DATA_SOURCE_UNAVAILABLE("DATA_SOURCE_UNAVAILABLE", "数据源暂不可用，请稍后重试");

    private final String code;

    private final String message;

    /** 绑定稳定代码与安全提示。 */
    DataQueryErrorCode(String code, String message) {
        this.code = code;
        this.message = message;
    }

    /** 返回稳定代码。 */
    @Override
    public String getCode() {
        return code;
    }

    /** 返回安全文案。 */
    @Override
    public String getMessage() {
        return message;
    }
}

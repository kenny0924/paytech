package com.paytech.risk.decision.domainservice.service;

/** Owner 内部可区分结果，由 Web 映射到稳定业务 Code。 */
public final class DictionaryConflictException extends RuntimeException {
    /** 字典业务冲突类别，由接口层映射为 Owner 错误码。 */
    public enum Reason implements com.paytech.foundation.common.enums.CodeEnum {
        NOT_FOUND("NOT_FOUND"),

        CODE_EXISTS("CODE_EXISTS"),

        CHANGED("CHANGED"),

        SELECTION_UNAVAILABLE("SELECTION_UNAVAILABLE");

        private final String code;
        /** 关联稳定的字典失败原因代码。 */
        Reason(String code) { this.code = code; }
        /** 返回稳定的字典失败原因代码。 */
        @Override public String getCode() { return code; }
    }
    private final Reason reason;
    /** 保存当前字典操作失败原因，由 HTTP 边界决定公开错误。 */
    public DictionaryConflictException(Reason reason) { super("字典操作未满足约定状态"); this.reason = reason; }
    /** 保留持久化失败原因，由 HTTP 边界转换为安全业务错误。 */
    public DictionaryConflictException(Reason reason, Throwable cause) {
        super("字典操作未满足约定状态", cause);
        this.reason = reason;
    }
    /** 返回供 HTTP 边界转换的字典业务失败原因。 */
    public Reason reason() { return reason; }
}

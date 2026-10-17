package com.paytech.foundation.common.enums;

/**
 * 带稳定编码的枚举契约。
 *
 * <p>
 * 编码用于数据库、接口、消息和日志等外部边界，一旦发布不得随枚举常量重命名而变化。
 *
 * @author Codex
 */
public interface CodeEnum {

    /**
     * 获取枚举编码；枚举为空时返回 {@code null}。
     *
     * @param codeEnum
     *            带编码的枚举
     * @return 稳定编码或 {@code null}
     */
    static String defaultCode(CodeEnum codeEnum) {
        return codeEnum == null ? null : codeEnum.getCode();
    }

    /**
     * 获取枚举编码；枚举为空时使用默认枚举。
     *
     * @param codeEnum
     *            带编码的枚举
     * @param defaultCodeEnum
     *            默认枚举
     * @return 稳定编码或 {@code null}
     */
    static String defaultCode(CodeEnum codeEnum, CodeEnum defaultCodeEnum) {
        return codeEnum == null ? defaultCode(defaultCodeEnum) : codeEnum.getCode();
    }

    /**
     * 获取稳定编码。
     *
     * @return 稳定编码
     */
    String getCode();
}

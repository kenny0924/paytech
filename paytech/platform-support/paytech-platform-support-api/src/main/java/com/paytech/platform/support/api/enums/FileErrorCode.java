package com.paytech.platform.support.api.enums;

import com.paytech.foundation.common.enums.CodeMessageEnum;

/**
 * 内部文件接口的错误码；只包含安全提示，不暴露存储坐标
 * <li>HTTP 状态：FILE_NOT_FOUND 404，FILE_STATE_CONFLICT 409，FILE_POLICY_REJECTED 422。</li>
 */
public enum FileErrorCode implements CodeMessageEnum {
    /** 文件或上传不存在，或不属于调用方来源系统。 */
    FILE_NOT_FOUND("FILE_NOT_FOUND", "文件不存在或不可访问"),

    /** 上传已过期或状态不允许完成，文件扫描未通过或对象不可用。 */
    FILE_STATE_CONFLICT("FILE_STATE_CONFLICT", "当前文件状态不允许此操作"),

    /** 类型或大小不符合扫描策略。 */
    FILE_POLICY_REJECTED("FILE_POLICY_REJECTED", "文件类型或大小不符合要求");

    private final String code;

    private final String message;

    /** 绑定稳定代码与安全提示。 */
    FileErrorCode(String code, String message) {
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

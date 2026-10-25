package com.paytech.client.master.api.enums;

import com.paytech.foundation.common.enums.CodeMessageEnum;

/**
 * 运营主体与 Partner 运营接口需要调用方区分处理的错误。
 * <li>Operations BFF 与运营页按 Code 提示：不存在、版本冲突需刷新、取值非法。Client 不存在复用 CLIENT_NOT_FOUND。</li>
 */
public enum EntityAdminErrorCode implements CodeMessageEnum {
    OPERATING_ENTITY_NOT_FOUND("OPERATING_ENTITY_NOT_FOUND", "经营主体不存在"),

    LOCAL_PARTNER_NOT_FOUND("LOCAL_PARTNER_NOT_FOUND", "Local Partner 不存在"),

    ENTITY_CHANGE_VERSION_CONFLICT("ENTITY_CHANGE_VERSION_CONFLICT", "主档已被修改，请刷新后重试"),

    ENTITY_ATTRIBUTE_INVALID("ENTITY_ATTRIBUTE_INVALID", "主档属性取值不符合约定");

    private final String code;

    private final String message;

    /** 固定 Code 与安全默认消息。 */
    EntityAdminErrorCode(String code, String message) {
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

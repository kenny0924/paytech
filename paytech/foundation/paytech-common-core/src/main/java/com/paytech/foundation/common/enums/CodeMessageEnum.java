package com.paytech.foundation.common.enums;

/**
 * 同时提供稳定编码和可安全外显默认消息的枚举契约。
 *
 * <p>
 * 编码一旦发布不得随枚举常量重命名而变化；默认消息不得包含凭据、个人信息或内部异常细节。
 *
 * @author Codex
 */
public interface CodeMessageEnum extends CodeEnum {

    /**
     * 获取可安全外显的默认消息。
     *
     * @return 默认消息
     */
    String getMessage();
}

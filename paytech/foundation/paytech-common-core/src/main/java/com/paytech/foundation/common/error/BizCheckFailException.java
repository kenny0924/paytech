package com.paytech.foundation.common.error;

import java.io.Serial;

/**
 * 普通业务校验未通过时的首选异常，使用公共 BUSINESS_CHECK_FAILED 编码和 HTTP 400。
 *
 * <p>用安全消息说明拒绝原因，无需为每条业务检查新增错误码；消息不得包含敏感明文或完整请求。
 *
 * @author Codex
 */
public class BizCheckFailException extends BusinessException {

    @Serial
    private static final long serialVersionUID = 1L;

    public BizCheckFailException(String message) {
        super(CommonErrorCode.BUSINESS_CHECK_FAILED, message);
    }

    public BizCheckFailException(String message, Throwable cause) {
        super(CommonErrorCode.BUSINESS_CHECK_FAILED, message, cause);
    }
}

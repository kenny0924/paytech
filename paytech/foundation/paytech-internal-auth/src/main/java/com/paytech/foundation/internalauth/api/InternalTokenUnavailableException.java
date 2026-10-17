package com.paytech.foundation.internalauth.api;

import com.paytech.foundation.common.error.CommonErrorCode;
import com.paytech.foundation.common.error.ServiceUnavailableException;

/**
 * 取不到 Token 或校验公钥；调用方与校验方都按失败关闭处理（503）。
 *
 * @author Claude
 */
public final class InternalTokenUnavailableException extends ServiceUnavailableException {

    /**
     * 创建不可用异常。
     *
     * @param cause 原始异常，可为 null
     */
    public InternalTokenUnavailableException(Throwable cause) {
        super(CommonErrorCode.SERVICE_UNAVAILABLE, cause);
    }
}

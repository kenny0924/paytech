package com.paytech.platform.support.referencedata.client;

import java.io.Serial;

import com.paytech.foundation.common.error.CommonErrorCode;
import com.paytech.foundation.common.error.ErrorException;

/**
 * 种类不可读：系统异常（SERVICE_UNAVAILABLE）。
 * <li>未配置：该种类没有在 {@code platform-support.reference-data.client.kinds} 中开启，属于宿主配置错误。</li>
 * <li>未就绪：该种类尚未首次加载成功，readiness 同时为 OUT_OF_SERVICE。</li>
 */
public class ReferenceDataUnavailableException extends ErrorException {

    @Serial
    private static final long serialVersionUID = 1L;

    private final ReferenceDataKind kind;

    private ReferenceDataUnavailableException(ReferenceDataKind kind, String message) {
        super(CommonErrorCode.SERVICE_UNAVAILABLE, message);
        this.kind = kind;
    }

    /**
     * 种类未在配置中开启
     *
     * @param kind 种类
     * @return 异常
     */
    public static ReferenceDataUnavailableException notConfigured(ReferenceDataKind kind) {
        return new ReferenceDataUnavailableException(kind, "参考数据未配置: " + kind.getCode());
    }

    /**
     * 种类尚未首次加载成功
     *
     * @param kind 种类
     * @return 异常
     */
    public static ReferenceDataUnavailableException notReady(ReferenceDataKind kind) {
        return new ReferenceDataUnavailableException(kind, "参考数据未就绪: " + kind.getCode());
    }

    /** 不可读的种类。 */
    public ReferenceDataKind getKind() {
        return kind;
    }
}

package com.paytech.platform.support.referencedata.client;

import java.io.Serial;

import com.paytech.foundation.common.error.CommonErrorCode;
import com.paytech.foundation.common.error.ErrorException;

/**
 * 快照中没有该编码：系统异常（INTERNAL_ERROR）。
 * <li>保持系统异常语义：消息消费方按失败重投（例如 Platform 刚启用的币种在下一次刷新后才进入快照），不进死信。</li>
 * <li>需要把"不存在"转换为业务错误的调用方使用 {@link ReferenceCatalog#find}。</li>
 */
public class ReferenceDataNotFoundException extends ErrorException {

    @Serial
    private static final long serialVersionUID = 1L;

    private final ReferenceDataKind kind;

    private final String code;

    /**
     * 创建"参考数据不存在"异常
     *
     * @param kind 种类
     * @param code 查找的编码
     */
    public ReferenceDataNotFoundException(ReferenceDataKind kind, String code) {
        super(CommonErrorCode.INTERNAL_ERROR, "参考数据不存在: " + kind.getCode() + " " + code);
        this.kind = kind;
        this.code = code;
    }

    /** 查找的种类。 */
    public ReferenceDataKind getKind() {
        return kind;
    }

    /** 查找的编码。 */
    public String getReferenceCode() {
        return code;
    }
}

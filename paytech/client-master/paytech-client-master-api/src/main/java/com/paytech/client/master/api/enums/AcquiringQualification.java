package com.paytech.client.master.api.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/**
 * Operating Entity 的收单资质档位。
 * <li>决定该 OE 能否承接 Client 及可承接的业务模式；commercial 解析时按此过滤候选。</li>
 */
public enum AcquiringQualification implements CodeEnum {
    /** 只能作为自身商户收单，不能承接 Client。 */
    MERCHANT("MERCHANT"),

    /** 经收单行批准转售，只承接 MOR。 */
    MERCHANT_RESALE_APPROVED("MERCHANT_RESALE_APPROVED"),

    /** 支付服务商资质，可承接 MOR 与 PSP。 */
    PAYFAC("PAYFAC");

    private final String code;

    /** 固定 Code。 */
    AcquiringQualification(String code) {
        this.code = code;
    }

    /** 稳定 Code。 */
    @Override
    public String getCode() {
        return code;
    }

    /**
     * 按 Code 解析
     *
     * @param code 待解析的 Code
     * @return 对应档位；不认识的 Code 返回 null，由调用方按自身合同拒绝
     */
    public static AcquiringQualification fromCode(String code) {
        for (AcquiringQualification value : values()) {
            if (value.code.equals(code)) {
                return value;
            }
        }
        return null;
    }
}

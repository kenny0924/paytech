package com.paytech.platform.support.integration.domain.execution;

import java.util.Map;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 沙箱验证账号材料：替代 Vault 读取的渠道账号身份、参数与凭据；只在本次验证运行内存中使用。 */
@Getter
@AllArgsConstructor
public class VerifyAccountMaterial {

    /** 渠道编码。 */
    private final String channelCode;

    /** 渠道账号标识。 */
    private final String channelAccountId;

    /** 经营主体编号。 */
    private final String entityNo;

    /** 普通参数。 */
    private final Map<String, String> parameters;

    /** 凭据明文；交给流程前包装为受控句柄。 */
    private final Map<String, String> credentials;

    @Override
    public String toString() {
        return "VerifyAccountMaterial[" + channelCode + "/" + channelAccountId + ", credentials redacted]";
    }
}

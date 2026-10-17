package com.paytech.client.contract.api.onboarding.response;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

/** CCT 受控目录的公开响应；不含规范化策略或个人资料。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class IdentifierOptionResponseBody {
    /** 证件或登记类型稳定代码。 */
    private String code;
    /** 证件或登记类型展示名称。 */
    private String label;
    /**
     * 签发国要求
     * <li>REQUIRED：须选择签发国；NONE：全球通用标识，无签发国。</li>
     */
    private String issuingCountryMode;
}

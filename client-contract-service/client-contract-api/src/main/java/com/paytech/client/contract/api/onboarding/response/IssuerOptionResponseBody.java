package com.paytech.client.contract.api.onboarding.response;

import java.util.List;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

/** CCT 受控目录的公开响应；不含规范化策略或个人资料。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class IssuerOptionResponseBody {
    /** 准确签发机构作用域；不是由国家派生。 */
    private String scopeKey;
    /** 签发机构展示名称。 */
    private String label;
    /** 准确签发国；GLOBAL 作用域时为空。 */
    private String issuingCountry;
}

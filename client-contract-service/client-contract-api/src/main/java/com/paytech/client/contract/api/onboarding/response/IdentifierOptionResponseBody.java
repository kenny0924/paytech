package com.paytech.client.contract.api.onboarding.response;

import java.util.List;
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
    /** 此类型允许的准确签发机构集合。 */
    private List<IssuerOptionResponseBody> issuers;
}

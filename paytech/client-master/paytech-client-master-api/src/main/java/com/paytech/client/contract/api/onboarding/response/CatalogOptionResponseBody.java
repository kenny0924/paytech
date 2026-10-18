package com.paytech.client.contract.api.onboarding.response;

import java.util.List;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

/** CCT 受控目录的公开响应；不含规范化策略或个人资料。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CatalogOptionResponseBody {
    /** Owner 维护的稳定代码。 */
    private String code;
    /** 受控展示名称。 */
    private String label;
}

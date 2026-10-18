package com.paytech.client.contract.api.onboarding.response;

import java.util.List;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

/** CCT 受控目录的公开响应；不含规范化策略或个人资料。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ClientFormOptionsResponseBody {
    /** 当前个人或组织类型。 */
    private String clientType;
    /** 目录状态；不代表提交或审核就绪。 */
    private String catalogStatus;
    /** 通用组织类型；个人为空列表。 */
    private List<CatalogOptionResponseBody> organizationTypes;
    /** 此客户类型可声明的经营模式。 */
    private List<CatalogOptionResponseBody> businessModels;
    /** 此客户类型可选证件及签发国要求。 */
    private List<IdentifierOptionResponseBody> identifierTypes;
}

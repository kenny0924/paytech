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
    /** 当前资料国家；未选择时为空。 */
    private String countryCode;
    /** 目录状态；不代表提交或审核就绪。 */
    private String catalogStatus;
    /** 当前注册国适用的组织类型。 */
    private List<CatalogOptionResponseBody> organizationTypes;
    /** 此客户类型可声明的经营模式。 */
    private List<CatalogOptionResponseBody> businessModels;
    /** 此客户类型可选证件及准确签发机构。 */
    private List<IdentifierOptionResponseBody> identifierTypes;
}

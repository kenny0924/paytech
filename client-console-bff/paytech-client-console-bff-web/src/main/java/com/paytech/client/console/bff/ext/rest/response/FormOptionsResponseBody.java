package com.paytech.client.console.bff.ext.rest.response;
import java.util.List;
import lombok.Data;
import lombok.AllArgsConstructor;
import com.paytech.client.contract.api.onboarding.response.*;
import com.paytech.risk.api.compliance.MaterialRequirementsModels.RequirementBody;
/** 页面选项由两个 Owner 提供，分别保留不可用状态。 */
@Data
@AllArgsConstructor
public class FormOptionsResponseBody {
    /** 当前客户类型。 */
    private String clientType;
    /** 主资料国家，可空。 */
    private String countryCode;
    /** CCT 目录状态，不能解释为提交就绪。 */
    private String catalogStatus;
    /** 适用注册国的组织类型。 */
    private List<CatalogOptionResponseBody> organizationTypes;
    /** 此类型可选经营模式。 */
    private List<CatalogOptionResponseBody> businessModels;
    /** 证件类型及独立签发国/机构。 */
    private List<IdentifierOptionResponseBody> identifierTypes;
    /** Risk 材料要求状态。 */
    private String requirementsStatus;
    /** 当前准确国家的材料要求。 */
    private List<RequirementBody> requirements;
}

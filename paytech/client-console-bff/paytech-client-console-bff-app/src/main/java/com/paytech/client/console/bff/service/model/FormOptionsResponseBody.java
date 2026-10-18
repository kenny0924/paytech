package com.paytech.client.console.bff.service.model;
import java.util.List;
import lombok.Data;
import lombok.AllArgsConstructor;
import com.paytech.client.contract.api.onboarding.response.*;
import com.paytech.compliance.api.MaterialRequirementsModels.RequirementBody;
/** 页面选项由两个 Owner 提供，分别保留不可用状态。 */
@Data
@AllArgsConstructor
public class FormOptionsResponseBody {
    /** 当前客户类型。 */
    private String clientType;
    /** CCT 目录状态，不能解释为提交就绪。 */
    private String catalogStatus;
    /** 通用组织类型；个人为空列表。 */
    private List<CatalogOptionResponseBody> organizationTypes;
    /** 此类型可选经营模式。 */
    private List<CatalogOptionResponseBody> businessModels;
    /** 证件类型及签发国要求。 */
    private List<IdentifierOptionResponseBody> identifierTypes;
    /** Risk 材料要求状态。 */
    private String requirementsStatus;
    /** 主主体资格类型的通用材料要求。 */
    private List<RequirementBody> requirements;
}

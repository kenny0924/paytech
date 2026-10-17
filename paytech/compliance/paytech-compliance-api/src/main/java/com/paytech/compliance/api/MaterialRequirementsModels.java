package com.paytech.risk.api.compliance;
import java.util.List;
/** Risk 材料要求最小私网合同；程序修订不是客户资料版本。 */
public final class MaterialRequirementsModels {
    /** 仅作为模型容器。 */
    private MaterialRequirementsModels() {}

    /**
     * @param programId
     *            精确程序身份
     * @param programVersionId
     *            当前经批准的程序修订
     * @param items
     *            该修订真实材料目录
     */
    public record RequirementsResponseBody(String programId,String programVersionId,List<RequirementBody> items) {}

    /**
     * @param requirementCode
     *            稳定材料要求代码
     * @param documentType
     *            受控材料类型
     * @param requiredFlag
     *            是否必需
     */
    public record RequirementBody(String requirementCode,String documentType,boolean requiredFlag) {}
}

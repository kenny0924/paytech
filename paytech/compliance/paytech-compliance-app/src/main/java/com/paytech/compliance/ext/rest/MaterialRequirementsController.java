package com.paytech.risk.ext.rest.compliance;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.RestController;

import com.paytech.foundation.common.error.InvalidParameterException;
import com.paytech.risk.api.compliance.MaterialRequirementsApi;
import com.paytech.risk.api.compliance.MaterialRequirementsModels.RequirementBody;
import com.paytech.risk.api.compliance.MaterialRequirementsModels.RequirementsResponseBody;
import com.paytech.risk.compliance.core.enums.MaterialQualificationType;
import com.paytech.risk.compliance.service.processor.MaterialRequirementsProcessor;
import com.paytech.risk.compliance.service.processor.request.MaterialRequirementsRequest;
import com.paytech.risk.compliance.service.processor.response.MaterialRequirementsResponse;

import lombok.AllArgsConstructor;

/**
 * CCT 与 Client BFF 读取通用材料目录的私网入口
 * <li>仅在 onboarding.materials.enabled=true 时注册。</li>
 * <li>X-Caller-Service 暂不校验，调用方鉴权待后续统一安全方案处理；Header 保留以兼容既有合同。</li>
 */
@RestController
@AllArgsConstructor
@ConditionalOnProperty(name = "onboarding.materials.enabled", havingValue = "true")
public class MaterialRequirementsController implements MaterialRequirementsApi {
    private final MaterialRequirementsProcessor processor;

    /** 校验资格类型后委托 risk-compliance 读取。 */
    @Override
    public RequirementsResponseBody read(String caller, String qualificationType) {
        MaterialQualificationType type = MaterialQualificationType.ofCode(qualificationType);
        if (type == null) {
            throw new InvalidParameterException("材料资格类型无效");
        }
        return toResponseBody(processor.process(new MaterialRequirementsRequest(type)));
    }

    /** BIGINT 身份按十进制字符串输出。 */
    RequirementsResponseBody toResponseBody(MaterialRequirementsResponse response) {
        return new RequirementsResponseBody(Long.toString(response.getProgramId()),
            Long.toString(response.getProgramVersionId()),
            response.getItems().stream()
                .map(item -> new RequirementBody(item.getRequirementCode(), item.getDocumentType(), item.isRequired()))
                .toList());
    }
}

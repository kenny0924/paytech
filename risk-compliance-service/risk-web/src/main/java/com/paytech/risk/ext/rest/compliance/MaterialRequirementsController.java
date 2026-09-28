package com.paytech.risk.ext.rest.compliance;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import com.paytech.foundation.common.error.InvalidParameterException;
import com.paytech.risk.api.compliance.*;
import com.paytech.risk.api.enums.MaterialQualificationType;
import com.paytech.risk.api.compliance.MaterialRequirementsModels.RequirementsResponseBody;
import com.paytech.risk.service.processor.MaterialRequirementsProcessor;
/** CCT 与 Client BFF 的材料目录私网能力；Caller Header 仅防误用，不等于强服务认证。 */
@RestController @lombok.AllArgsConstructor
@ConditionalOnProperty(name="onboarding.materials.enabled",havingValue="true")
public class MaterialRequirementsController implements MaterialRequirementsApi {
    private final MaterialRequirementsProcessor processor;
    /** 当前只发布 Client 入驻三类材料要求，不替代运营权限。 */
    @Override public RequirementsResponseBody read(String caller,String type,String country) {
        if (!java.util.Set.of("client-contract-service","client-console-bff").contains(caller)) { throw new ResponseStatusException(HttpStatus.NOT_FOUND); }
        if (type==null || country==null || !country.matches("[A-Z]{2}")) throw new InvalidParameterException("材料主体类型和国家代码无效");
        try { return processor.read(MaterialQualificationType.fromCode(type),country); }
        catch (IllegalArgumentException invalid) { throw new InvalidParameterException("材料资格类型无效"); }
    }
}

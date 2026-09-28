package com.paytech.risk.api.compliance;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.service.annotation.*;
import com.paytech.risk.api.compliance.MaterialRequirementsModels.RequirementsResponseBody;
/** CCT 与 Client BFF 授权查询的私网通用材料目录；不创建 Case 或执行审核。 */
@HttpExchange("/internal/due-diligence")
public interface MaterialRequirementsApi {
    /** 按准确主体类型与国家读取生效的材料要求，未配置明确不可用。 */
    @GetExchange("/material-requirements")
    RequirementsResponseBody read(@RequestHeader("X-Caller-Service") String caller,
        @RequestParam("qualificationType") String qualificationType,@RequestParam("countryCode") String countryCode);
}

package com.paytech.compliance.api;

import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;

import com.paytech.compliance.api.MaterialRequirementsModels.RequirementsResponseBody;
/** CCT 与 Client BFF 授权查询的私网通用材料目录；不创建 Case 或执行审核。 */
@HttpExchange("/internal/due-diligence")
public interface MaterialRequirementsApi {
    /**
     * 按主体资格类型读取生效的通用材料要求，未配置明确不可用。
     * <li>材料不按国家区分；材料是否充分由人工审核判断。</li>
     *
     * @param caller
     *            私网调用方
     * @param qualificationType
     *            INDIVIDUAL_KYC、ORGANIZATION_KYB 或 RELATED_PERSON_KYC
     * @return 当前批准程序修订及其材料要求
     */
    @GetExchange("/material-requirements")
    RequirementsResponseBody read(@RequestHeader("X-Caller-Service") String caller,
        @RequestParam("qualificationType") String qualificationType);
}

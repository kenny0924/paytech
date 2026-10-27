package com.paytech.compliance.ext.rest;

import org.springframework.web.bind.annotation.RestController;

import com.paytech.compliance.api.ProgramResolutionApi;
import com.paytech.compliance.api.response.ProgramResolutionResponseBody;
import com.paytech.compliance.core.enums.ReviewCodes;
import com.paytech.compliance.service.processor.ResolveProgramProcessor;
import com.paytech.compliance.service.processor.request.ResolveProgramRequest;

import lombok.AllArgsConstructor;

/**
 * CP-1 程序解析的私网入口；只读目录查询，始终注册（client-master 渲染采集 Schema 与提交前都需要）
 * <li>X-Caller-Service 暂不校验，与 I01 一致，调用方鉴权待统一安全方案处理。</li>
 */
@RestController
@AllArgsConstructor
public class ProgramResolutionController implements ProgramResolutionApi {
    private final ResolveProgramProcessor processor;

    /** 转换查询参数后解析；档次缺省 STANDARD。 */
    @Override
    public ProgramResolutionResponseBody resolve(String caller, String jurisdiction, String partyType, String roleType, String tier,
        String responsiblePartnerId) {
        return ClientReviewWireConverter.program(processor.process(new ResolveProgramRequest(partyType, roleType,
            tier == null ? ReviewCodes.TIER_STANDARD : tier, jurisdiction,
            responsiblePartnerId == null ? null : ClientReviewRegistrationController.positive(responsiblePartnerId, "responsiblePartnerId 无效"))));
    }
}

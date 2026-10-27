package com.paytech.compliance.service.processor;

import org.springframework.stereotype.Component;

import com.paytech.compliance.domain.ResolvedProgram;
import com.paytech.compliance.domainservice.service.DueDiligenceProgramService;
import com.paytech.compliance.service.processor.request.ResolveProgramRequest;
import com.paytech.compliance.service.processor.response.ProgramResolutionResponse;
import com.paytech.foundation.common.error.InvalidParameterException;
import com.paytech.foundation.processor.AbstractProcessor;

import lombok.AllArgsConstructor;

/** CP-1 程序解析；只读。关联人员只有个人主体。 */
@Component
@AllArgsConstructor
public class ResolveProgramProcessor extends AbstractProcessor<ResolveProgramRequest, ProgramResolutionResponse> {
    private final ClientReviewCommandSupport support;
    private final DueDiligenceProgramService programs;

    /** 关联人员只能是个人。 */
    @Override
    protected void validate(ResolveProgramRequest request) {
        if ("RELATED_PERSON".equals(request.getRoleType()) && !"INDIVIDUAL".equals(request.getPartyType())) {
            throw new InvalidParameterException("关联人员程序只适用于个人主体");
        }
    }

    /** 解析并转换为 Owner 结果。 */
    @Override
    protected ProgramResolutionResponse processRequest(ResolveProgramRequest request) {
        ResolvedProgram program = programs.resolve(request.getPartyType(), request.getRoleType(), request.getTier(),
            request.getJurisdictionCode(), request.getResponsiblePartnerId(), support.now());
        return ProgramResponseAssembler.from(program);
    }
}

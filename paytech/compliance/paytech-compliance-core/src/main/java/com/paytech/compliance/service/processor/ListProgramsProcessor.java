package com.paytech.compliance.service.processor;

import org.springframework.stereotype.Component;

import com.paytech.compliance.domainservice.service.DueDiligenceProgramService;
import com.paytech.compliance.service.processor.request.ListProgramsRequest;
import com.paytech.compliance.service.processor.response.ProgramCatalogResponse;
import com.paytech.foundation.processor.AbstractProcessor;

import lombok.AllArgsConstructor;

/** O14 运营程序目录：只读，列出程序全部版本及模板（含 DRAFT、RETIRED），供工作台查看当前配置。 */
@Component
@AllArgsConstructor
public class ListProgramsProcessor extends AbstractProcessor<ListProgramsRequest, ProgramCatalogResponse> {
    private final DueDiligenceProgramService programs;

    @Override
    protected ProgramCatalogResponse processRequest(ListProgramsRequest request) {
        return new ProgramCatalogResponse(programs.listCatalog(request.getJurisdictionCode(), request.getStatus()).stream()
            .map(ProgramResponseAssembler::from).toList());
    }
}

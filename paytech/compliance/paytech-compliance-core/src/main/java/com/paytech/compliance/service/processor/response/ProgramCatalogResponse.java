package com.paytech.compliance.service.processor.response;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 运营程序目录。 */
@Getter
@AllArgsConstructor
public class ProgramCatalogResponse {
    /** 程序版本，按辖区、程序代码、版本号倒序。 */
    private final List<ProgramResolutionResponse> items;
}

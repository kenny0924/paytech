package com.paytech.client.console.bff.service.processor.request;

import com.paytech.client.console.bff.service.model.ClientActor;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** 入驻采集 Schema 的内部请求；查询参数的格式与组合已在 Controller 校验。 */
@Getter
@AllArgsConstructor
public class GetClientFormSchemaRequest {

    /** 入站边界已解析的可信身份 */
    @NotNull(message = "可信身份不能为空")
    private final ClientActor actor;

    /** 可选申请 ID；有值时 Owner 返回该申请冻结的 Schema 版本 */
    private final String applicationId;

    /** 可选辖区代码 */
    private final String jurisdiction;

    /** 可选主体类型 */
    private final String partyType;

    /** 可选组织类型 */
    private final String organizationType;
}

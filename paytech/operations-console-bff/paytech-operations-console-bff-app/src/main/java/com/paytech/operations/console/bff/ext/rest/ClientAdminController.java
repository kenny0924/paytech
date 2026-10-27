package com.paytech.operations.console.bff.ext.rest;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.operations.console.bff.integration.EntityAdminClient;
import com.paytech.operations.console.bff.integration.OnboardingAdminClient;
import com.paytech.client.master.api.onboardingadmin.response.AdminClientSummaryResponseBody;
import com.paytech.operations.console.bff.service.model.OperatorContext;
import com.paytech.operations.console.bff.service.model.clientadmin.response.ClientPageResponseBody;
import com.paytech.operations.console.bff.service.model.clientreview.response.ResponsiblePartnerListResponseBody;
import com.paytech.operations.console.bff.service.processor.ClientDetailsProcessor;
import com.paytech.operations.console.bff.service.processor.request.ClientDetailsRequest;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;

/**
 * CM-11 Client 列表 / 详情（只读）；路由权限 CLIENT_READ（块 138）
 * <li>详情由 Processor 聚合 client-master 主档与 compliance 资格；列表与 PayFac 选项为单 Owner 转发。</li>
 * <li>响应含法定名称与关系人姓名，/client-admin 只记元数据日志。</li>
 */
@RestController
@AllArgsConstructor
@RequestMapping("/client-admin/v1")
public class ClientAdminController {
    private static final String ID = "^[1-9][0-9]{0,18}$";

    private final EntityAdminClient entityAdmin;
    private final OnboardingAdminClient onboardingAdmin;
    private final ClientDetailsProcessor details;

    /** Client 分页，可按状态、辖区与责任 PayFac 过滤。 */
    @GetMapping("/clients")
    public PagingResponseBody<AdminClientSummaryResponseBody> clients(OperatorContext operator,
        @RequestParam(required = false) @Min(1) @Max(100000) Integer page, @RequestParam(required = false) @Min(1) @Max(100) Integer size,
        @RequestParam(required = false) @Pattern(regexp = "^[A-Z][A-Z0-9_]{0,63}$") String status,
        @RequestParam(required = false) @Pattern(regexp = "^[A-Z][A-Z0-9-]{1,15}$") String jurisdiction,
        @RequestParam(required = false) @Pattern(regexp = ID) String responsiblePartnerId) {
        return onboardingAdmin.clients(operator, page, size, status, jurisdiction, responsiblePartnerId);
    }

    /** Client 详情：主档、关系人、申请历史与资格。 */
    @GetMapping("/clients/{clientId}")
    public ClientPageResponseBody client(OperatorContext operator, @PathVariable @Pattern(regexp = ID) String clientId) {
        return details.process(new ClientDetailsRequest(operator, clientId));
    }

    /** 列表过滤用的 PayFac 选项。 */
    @GetMapping("/responsible-partners")
    public ResponsiblePartnerListResponseBody responsiblePartners(OperatorContext operator) {
        return entityAdmin.responsiblePartners(operator);
    }
}

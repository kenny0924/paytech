package com.paytech.operations.console.bff.ext.rest;

import static com.paytech.operations.console.bff.ext.rest.RiskListController.requireQueryKeys;

import java.util.List;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.paytech.foundation.web.paging.PagingRequestBody;
import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.operations.console.bff.integration.CommercialAgreementClient;
import com.paytech.operations.console.bff.integration.CommercialConsoleQueryClient;
import com.paytech.operations.console.bff.service.model.CommercialAgreementModels.AddAgreementCapabilityRequestBody;
import com.paytech.operations.console.bff.service.model.CommercialAgreementModels.AgreementCapabilityResponseBody;
import com.paytech.operations.console.bff.service.model.CommercialAgreementModels.AgreementOverviewResponseBody;
import com.paytech.operations.console.bff.service.model.CommercialAgreementModels.AgreementResponseBody;
import com.paytech.operations.console.bff.service.model.CommercialAgreementModels.AgreementTransitionRequestBody;
import com.paytech.operations.console.bff.service.model.CommercialAgreementModels.CreateAgreementRequestBody;
import com.paytech.operations.console.bff.service.model.CommercialAgreementModels.RecordAgreementAcceptanceRequestBody;
import com.paytech.operations.console.bff.service.model.CommercialAgreementModels.ReplaceAgreementProductsRequestBody;
import com.paytech.operations.console.bff.service.model.CommercialAgreementModels.TerminateAgreementRequestBody;
import com.paytech.operations.console.bff.service.model.CommercialAgreementModels.UpdateAgreementCapabilityRequestBody;
import com.paytech.operations.console.bff.service.model.CommercialListModels.AgreementListItem;
import com.paytech.operations.console.bff.service.model.CommercialListModels.AgreementListRequestBody;
import com.paytech.operations.console.bff.service.model.OperatorContext;
import com.paytech.operations.console.bff.service.processor.CommercialAgreementOverviewProcessor;
import com.paytech.operations.console.bff.service.processor.request.CommercialAgreementOverviewRequest;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;

/**
 * 合同运营入口：合同列表、合同详情聚合、创建、提交签署、签署登记、激活、终止、订阅与开通项
 * <li>Gateway 为每个路由绑定 COMMERCIAL_CONTRACT_READ（GET）或 COMMERCIAL_CONTRACT_MANAGE（写入），并经 PLT-I02 实时授权。</li>
 * <li>详情经 Processor 聚合合同与当前 / 已排期条款版本；写入简单转发 commercial，操作人取自会话，不编排跨系统事务。</li>
 * <li>合同列表（第二切片 I2）简单转发 commercial AgreementQueryApi；status 可重复，分页按公共 PagingRequestBody 校验。</li>
 *
 * @author Claude
 */
@RestController
@AllArgsConstructor
@RequestMapping("/commercial-admin/v1/agreements")
public class CommercialAgreementController {
    private static final String ID = "[1-9][0-9]{0,18}";
    private static final String STATUS = "DRAFT|PENDING_SIGNATURE|SIGNED|ACTIVE|EXPIRED|TERMINATED|CANCELLED";
    private static final Set<String> LIST_KEYS = Set.of("clientId", "localPartnerId", "agreementType", "status", "entityNo",
        "agreementNo", "page", "size");

    private final CommercialAgreementClient client;
    private final CommercialConsoleQueryClient queryClient;
    private final CommercialAgreementOverviewProcessor overviewProcessor;

    /** C10 合同列表：按 Client、Partner、类型、状态（可多选）、OE、合同号前缀筛选分页。 */
    @GetMapping
    public PagingResponseBody<AgreementListItem> list(HttpServletRequest servletRequest,
        @RequestParam(value = "clientId", required = false) @Pattern(regexp = ID) String clientId,
        @RequestParam(value = "localPartnerId", required = false) @Pattern(regexp = ID) String localPartnerId,
        @RequestParam(value = "agreementType", required = false) @Pattern(regexp = "CLIENT|PARTNER") String agreementType,
        @RequestParam(value = "status", required = false) List<@Pattern(regexp = STATUS) String> status,
        @RequestParam(value = "entityNo", required = false) @Pattern(regexp = "[A-Z]{2}[0-9]{3}") String entityNo,
        @RequestParam(value = "agreementNo", required = false) @Pattern(regexp = "[A-Za-z0-9._-]{1,64}") String agreementNo,
        @RequestParam(value = "page", required = false) Integer page,
        @RequestParam(value = "size", required = false) Integer size) {
        CommercialQueryParameters.require(servletRequest, LIST_KEYS, Set.of("status"));
        PagingRequestBody paging = PagingRequestBody.of(page, size);
        return queryClient.listAgreements(new AgreementListRequestBody(clientId, localPartnerId, agreementType,
            status == null ? List.of() : List.copyOf(status), entityNo, agreementNo, paging.page(), paging.size()));
    }

    /** C01 合同详情聚合。 */
    @GetMapping("/{agreementId}")
    public AgreementOverviewResponseBody detail(HttpServletRequest servletRequest,
        @PathVariable("agreementId") @Pattern(regexp = ID) String agreementId) {
        requireQueryKeys(servletRequest, Set.of());
        return overviewProcessor.process(new CommercialAgreementOverviewRequest(agreementId));
    }

    /** C02 创建 DRAFT 合同。 */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AgreementResponseBody create(HttpServletRequest servletRequest, OperatorContext operator,
        @RequestBody @NotNull @Valid CreateAgreementRequestBody body) {
        requireQueryKeys(servletRequest, Set.of());
        return client.create(operator, body);
    }

    /** C03 提交签署。 */
    @PostMapping("/{agreementId}/submit-for-signature")
    public AgreementResponseBody submitForSignature(HttpServletRequest servletRequest, OperatorContext operator,
        @PathVariable("agreementId") @Pattern(regexp = ID) String agreementId,
        @RequestBody @NotNull @Valid AgreementTransitionRequestBody body) {
        requireQueryKeys(servletRequest, Set.of());
        return client.submitForSignature(operator, agreementId, body);
    }

    /** C04 登记一个 Party 的签署结果。 */
    @PostMapping("/{agreementId}/parties/{partyId}/acceptance")
    public AgreementResponseBody recordAcceptance(HttpServletRequest servletRequest, OperatorContext operator,
        @PathVariable("agreementId") @Pattern(regexp = ID) String agreementId,
        @PathVariable("partyId") @Pattern(regexp = ID) String partyId,
        @RequestBody @NotNull @Valid RecordAgreementAcceptanceRequestBody body) {
        requireQueryKeys(servletRequest, Set.of());
        return client.recordAcceptance(operator, agreementId, partyId, body);
    }

    /** C05 激活已签署合同。 */
    @PostMapping("/{agreementId}/activate")
    public AgreementResponseBody activate(HttpServletRequest servletRequest, OperatorContext operator,
        @PathVariable("agreementId") @Pattern(regexp = ID) String agreementId,
        @RequestBody @NotNull @Valid AgreementTransitionRequestBody body) {
        requireQueryKeys(servletRequest, Set.of());
        return client.activate(operator, agreementId, body);
    }

    /** C06 终止 ACTIVE 合同。 */
    @PostMapping("/{agreementId}/terminate")
    public AgreementResponseBody terminate(HttpServletRequest servletRequest, OperatorContext operator,
        @PathVariable("agreementId") @Pattern(regexp = ID) String agreementId,
        @RequestBody @NotNull @Valid TerminateAgreementRequestBody body) {
        requireQueryKeys(servletRequest, Set.of());
        return client.terminate(operator, agreementId, body);
    }

    /** C07 整体替换订阅产品。 */
    @PutMapping("/{agreementId}/products")
    public AgreementResponseBody replaceProducts(HttpServletRequest servletRequest, OperatorContext operator,
        @PathVariable("agreementId") @Pattern(regexp = ID) String agreementId,
        @RequestBody @NotNull @Valid ReplaceAgreementProductsRequestBody body) {
        requireQueryKeys(servletRequest, Set.of());
        return client.replaceProducts(operator, agreementId, body);
    }

    /** C08 新增开通项。 */
    @PostMapping("/{agreementId}/capabilities")
    @ResponseStatus(HttpStatus.CREATED)
    public AgreementCapabilityResponseBody addCapability(HttpServletRequest servletRequest, OperatorContext operator,
        @PathVariable("agreementId") @Pattern(regexp = ID) String agreementId,
        @RequestBody @NotNull @Valid AddAgreementCapabilityRequestBody body) {
        requireQueryKeys(servletRequest, Set.of());
        return client.addCapability(operator, agreementId, body);
    }

    /** C09 整体改写开通项。 */
    @PutMapping("/{agreementId}/capabilities/{agreementCapabilityId}")
    public AgreementCapabilityResponseBody updateCapability(HttpServletRequest servletRequest, OperatorContext operator,
        @PathVariable("agreementId") @Pattern(regexp = ID) String agreementId,
        @PathVariable("agreementCapabilityId") @Pattern(regexp = ID) String agreementCapabilityId,
        @RequestBody @NotNull @Valid UpdateAgreementCapabilityRequestBody body) {
        requireQueryKeys(servletRequest, Set.of());
        return client.updateCapability(operator, agreementId, agreementCapabilityId, body);
    }
}

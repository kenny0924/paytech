package com.paytech.operations.console.bff.ext.rest;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.paytech.client.master.api.entityadmin.request.ChangeAcquiringQualificationRequestBody;
import com.paytech.client.master.api.entityadmin.request.ReplacePartnerRolesRequestBody;
import com.paytech.client.master.api.entityadmin.response.LocalPartnerDetailResponseBody;
import com.paytech.client.master.api.entityadmin.response.LocalPartnerSummaryResponseBody;
import com.paytech.client.master.api.entityadmin.response.OperatingEntityDetailResponseBody;
import com.paytech.client.master.api.entityadmin.response.OperatingEntitySummaryResponseBody;
import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.operations.console.bff.integration.EntityAdminClient;
import com.paytech.operations.console.bff.service.model.OperatorContext;
import com.paytech.operations.console.bff.service.model.entityadmin.response.LocalPartnerPageResponseBody;
import com.paytech.operations.console.bff.service.model.entityadmin.response.OperatingEntityPageResponseBody;
import com.paytech.operations.console.bff.service.processor.LocalPartnerDetailsProcessor;
import com.paytech.operations.console.bff.service.processor.OperatingEntityDetailsProcessor;
import com.paytech.operations.console.bff.service.processor.request.LocalPartnerDetailsRequest;
import com.paytech.operations.console.bff.service.processor.request.OperatingEntityDetailsRequest;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;

/**
 * B1—B6 经营主体与 Local Partner 主档维护；路由权限由 Gateway 路由码 + PLT-I02 统一授权（块 137）
 * <li>B2、B5 由 Processor 聚合 client-master 主档与 compliance 资格，其余为 client-master 单 Owner 转发。</li>
 * <li>请求与响应使用 client-master 管理合同的模型；写入按 changeVersion 防覆盖，不转发 Idempotency-Key。</li>
 * <li>响应含法定名称，/entity-admin 只记元数据日志。</li>
 */
@RestController
@AllArgsConstructor
@RequestMapping("/entity-admin/v1")
public class EntityAdminController {
    private static final String ID = "^[1-9][0-9]{0,18}$";
    private static final String CODE = "^[A-Z][A-Z0-9_]{0,63}$";
    private static final String ENTITY_NO = "^[A-Z]{2}[0-9]{3}$";

    private final EntityAdminClient client;
    private final OperatingEntityDetailsProcessor entityDetails;
    private final LocalPartnerDetailsProcessor partnerDetails;

    /** B1 经营主体列表，按 entityNo 升序；size 只允许 10、20、50、100（client-master 校验）。 */
    @GetMapping("/operating-entities")
    public PagingResponseBody<OperatingEntitySummaryResponseBody> listOperatingEntities(OperatorContext operator,
        @RequestParam(required = false) @Pattern(regexp = CODE) String status,
        @RequestParam(required = false) @Pattern(regexp = "^(CLIENT|PARTNER)$") String sourceType,
        @RequestParam(required = false) @Pattern(regexp = "^[A-Z]{2}$") String country,
        @RequestParam(required = false) @Min(1) @Max(100000) Integer page,
        @RequestParam(required = false) @Min(1) @Max(100) Integer size) {
        return client.listOperatingEntities(operator, status, sourceType, country, page, size);
    }

    /** B2 经营主体详情：主档 + OE 资格 + 提供方资格。 */
    @GetMapping("/operating-entities/{entityNo}")
    public OperatingEntityPageResponseBody operatingEntity(OperatorContext operator,
        @PathVariable @Pattern(regexp = ENTITY_NO) String entityNo) {
        return entityDetails.process(new OperatingEntityDetailsRequest(operator, entityNo));
    }

    /** B3 修改收单资质；返回修改后的主档详情。 */
    @PutMapping("/operating-entities/{entityNo}/acquiring-qualification")
    public OperatingEntityDetailResponseBody changeAcquiringQualification(OperatorContext operator,
        @PathVariable @Pattern(regexp = ENTITY_NO) String entityNo, @Valid @RequestBody ChangeAcquiringQualificationRequestBody request) {
        return client.changeAcquiringQualification(operator, entityNo, request);
    }

    /** B4 Local Partner 列表，按 localPartnerId 升序。 */
    @GetMapping("/local-partners")
    public PagingResponseBody<LocalPartnerSummaryResponseBody> listLocalPartners(OperatorContext operator,
        @RequestParam(required = false) @Pattern(regexp = CODE) String status,
        @RequestParam(required = false) @Pattern(regexp = "^(OE_PROVIDER|REFERRER)$") String role,
        @RequestParam(required = false) @Min(1) @Max(100000) Integer page,
        @RequestParam(required = false) @Min(1) @Max(100) Integer size) {
        return client.listLocalPartners(operator, status, role, page, size);
    }

    /** B5 Local Partner 详情：主档 + Partner 资格。 */
    @GetMapping("/local-partners/{localPartnerId}")
    public LocalPartnerPageResponseBody localPartner(OperatorContext operator,
        @PathVariable @Pattern(regexp = ID) String localPartnerId) {
        return partnerDetails.process(new LocalPartnerDetailsRequest(operator, localPartnerId));
    }

    /** B6 整体替换 Partner 角色；返回修改后的主档详情。 */
    @PutMapping("/local-partners/{localPartnerId}/roles")
    public LocalPartnerDetailResponseBody replacePartnerRoles(OperatorContext operator,
        @PathVariable @Pattern(regexp = ID) String localPartnerId, @Valid @RequestBody ReplacePartnerRolesRequestBody request) {
        return client.replacePartnerRoles(operator, localPartnerId, request);
    }
}

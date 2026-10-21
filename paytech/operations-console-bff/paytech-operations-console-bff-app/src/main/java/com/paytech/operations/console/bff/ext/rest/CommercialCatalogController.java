package com.paytech.operations.console.bff.ext.rest;

import static com.paytech.operations.console.bff.ext.rest.RiskListController.requireQueryKeys;

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
import com.paytech.operations.console.bff.integration.CommercialCatalogClient.CatalogAction;
import com.paytech.operations.console.bff.integration.CommercialCatalogClient;
import com.paytech.operations.console.bff.integration.CommercialConsoleQueryClient;
import com.paytech.operations.console.bff.service.model.CommercialCatalogModels.CatalogCapabilityResponseBody;
import com.paytech.operations.console.bff.service.model.CommercialCatalogModels.CatalogFeeEventResponseBody;
import com.paytech.operations.console.bff.service.model.CommercialCatalogModels.CreateProductCapabilityRequestBody;
import com.paytech.operations.console.bff.service.model.CommercialCatalogModels.ProductCatalogTransitionRequestBody;
import com.paytech.operations.console.bff.service.model.CommercialCatalogModels.UpdateFeeEventCapabilityRequestBody;
import com.paytech.operations.console.bff.service.model.CommercialCatalogModels.UpdateProductCapabilityRequestBody;
import com.paytech.operations.console.bff.service.model.CommercialChangeModels.BusinessChangeEntry;
import com.paytech.operations.console.bff.service.model.CommercialListModels.CatalogCapabilityListResponseBody;
import com.paytech.operations.console.bff.service.model.CommercialListModels.CatalogDimensionListResponseBody;
import com.paytech.operations.console.bff.service.model.CommercialListModels.CatalogFeeEventListResponseBody;
import com.paytech.operations.console.bff.service.model.CommercialListModels.CatalogProductListResponseBody;
import com.paytech.operations.console.bff.service.model.OperatorContext;
import com.paytech.operations.console.bff.service.processor.CommercialBusinessChangeProcessor;
import com.paytech.operations.console.bff.service.processor.request.CommercialBusinessChangeRequest;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;

/**
 * 商务产品目录（读取与运营维护）、维度清单与变更历史的运营入口
 * <li>Gateway 绑定：读取（产品、可开通项、计费事件、维度清单、变更历史）为 COMMERCIAL_CONTRACT_READ（与产品目录、变更历史菜单一致）；
 * 目录维护（新增 / 改写 / 停用 / 启用可开通项、计费事件挂接）为独立的 COMMERCIAL_CATALOG_MANAGE。</li>
 * <li>产品目录读写与维度清单简单转发 commercial ProductCatalogApi（不分页），操作人取自会话；变更历史经 Processor 为每条快照补充与
 * 上一快照的字段级比较。</li>
 *
 * @author Claude
 */
@RestController
@AllArgsConstructor
@RequestMapping("/commercial-admin/v1")
public class CommercialCatalogController {
    private static final String ID = "[1-9][0-9]{0,18}";
    private static final String CODE = "[A-Z][A-Z0-9_]{0,63}";
    private static final String CATALOG_STATUS = "DRAFT|ACTIVE|RETIRED";
    private static final String PRODUCT_CODE = "ACQUIRING|PAYOUT|FX|DISPUTE|TAX_INVOICE|DATA_ANALYTICS|PLATFORM";

    private final CommercialConsoleQueryClient client;
    private final CommercialCatalogClient catalogClient;
    private final CommercialBusinessChangeProcessor changeProcessor;

    /** K01 产品（L0）列表。 */
    @GetMapping("/products")
    public CatalogProductListResponseBody products(HttpServletRequest servletRequest,
        @RequestParam(value = "status", required = false) @Pattern(regexp = CATALOG_STATUS) String status) {
        requireQueryKeys(servletRequest, Set.of("status"));
        return client.listProducts(status);
    }

    /** K02 可开通项（L1）列表。 */
    @GetMapping("/products/capabilities")
    public CatalogCapabilityListResponseBody capabilities(HttpServletRequest servletRequest,
        @RequestParam(value = "productCode", required = false) @Pattern(regexp = PRODUCT_CODE) String productCode,
        @RequestParam(value = "status", required = false) @Pattern(regexp = CATALOG_STATUS) String status) {
        requireQueryKeys(servletRequest, Set.of("productCode", "status"));
        return client.listCapabilities(productCode, status);
    }

    /** K03 计费事件（L2）列表；termType 用于价格编辑时只列出该条款类型登记的事件。 */
    @GetMapping("/products/fee-events")
    public CatalogFeeEventListResponseBody feeEvents(HttpServletRequest servletRequest,
        @RequestParam(value = "productCode", required = false) @Pattern(regexp = PRODUCT_CODE) String productCode,
        @RequestParam(value = "termType", required = false) @Pattern(regexp = CODE) String termType) {
        requireQueryKeys(servletRequest, Set.of("productCode", "termType"));
        return client.listFeeEvents(productCode, termType);
    }

    /** K04 维度清单：代码登记的维度元数据（事实来源、判定时点、可用于开通 / 定价）。 */
    @GetMapping("/products/dimensions")
    public CatalogDimensionListResponseBody dimensions(HttpServletRequest servletRequest) {
        requireQueryKeys(servletRequest, Set.of());
        return client.listDimensions();
    }

    /** K05 新增可开通项及其可配维度。 */
    @PostMapping("/products/capabilities")
    @ResponseStatus(HttpStatus.CREATED)
    public CatalogCapabilityResponseBody createCapability(HttpServletRequest servletRequest, OperatorContext operator,
        @RequestBody @NotNull @Valid CreateProductCapabilityRequestBody body) {
        requireQueryKeys(servletRequest, Set.of());
        return catalogClient.createCapability(operator, body);
    }

    /** K06 整体改写可开通项的名称、归属产品与可配维度。 */
    @PutMapping("/products/capabilities/{productCapabilityId}")
    public CatalogCapabilityResponseBody updateCapability(HttpServletRequest servletRequest, OperatorContext operator,
        @PathVariable("productCapabilityId") @Pattern(regexp = ID) String productCapabilityId,
        @RequestBody @NotNull @Valid UpdateProductCapabilityRequestBody body) {
        requireQueryKeys(servletRequest, Set.of());
        return catalogClient.updateCapability(operator, productCapabilityId, body);
    }

    /** K07 停用可开通项：已开通的合同不受影响，新增开通被拒。 */
    @PostMapping("/products/capabilities/{productCapabilityId}/retire")
    public CatalogCapabilityResponseBody retireCapability(HttpServletRequest servletRequest, OperatorContext operator,
        @PathVariable("productCapabilityId") @Pattern(regexp = ID) String productCapabilityId,
        @RequestBody @NotNull @Valid ProductCatalogTransitionRequestBody body) {
        requireQueryKeys(servletRequest, Set.of());
        return catalogClient.transitionCapability(operator, productCapabilityId, CatalogAction.RETIRE, body);
    }

    /** K08 启用可开通项。 */
    @PostMapping("/products/capabilities/{productCapabilityId}/activate")
    public CatalogCapabilityResponseBody activateCapability(HttpServletRequest servletRequest, OperatorContext operator,
        @PathVariable("productCapabilityId") @Pattern(regexp = ID) String productCapabilityId,
        @RequestBody @NotNull @Valid ProductCatalogTransitionRequestBody body) {
        requireQueryKeys(servletRequest, Set.of());
        return catalogClient.transitionCapability(operator, productCapabilityId, CatalogAction.ACTIVATE, body);
    }

    /** K09 改写计费事件挂接的开通项种类（或无需开通）。 */
    @PutMapping("/products/fee-events/{productFeeEventId}/capability")
    public CatalogFeeEventResponseBody updateFeeEventCapability(HttpServletRequest servletRequest, OperatorContext operator,
        @PathVariable("productFeeEventId") @Pattern(regexp = ID) String productFeeEventId,
        @RequestBody @NotNull @Valid UpdateFeeEventCapabilityRequestBody body) {
        requireQueryKeys(servletRequest, Set.of());
        return catalogClient.updateFeeEventCapability(operator, productFeeEventId, body);
    }

    /** H01 一个聚合根（合同、条款方案、定价画像）的变更时间线，带字段级比较。 */
    @GetMapping("/business-changes")
    public PagingResponseBody<BusinessChangeEntry> businessChanges(HttpServletRequest servletRequest,
        @RequestParam("aggregateRootType") @Pattern(regexp = "AGREEMENT|COMMERCIAL_TERM_PLAN|PAYMENT_METHOD_PROFILE")
        String aggregateRootType,
        @RequestParam("aggregateRootId") @Pattern(regexp = ID) String aggregateRootId,
        @RequestParam(value = "page", required = false) Integer page,
        @RequestParam(value = "size", required = false) Integer size) {
        requireQueryKeys(servletRequest, Set.of("aggregateRootType", "aggregateRootId", "page", "size"));
        PagingRequestBody paging = PagingRequestBody.of(page, size);
        return changeProcessor.process(new CommercialBusinessChangeRequest(aggregateRootType, aggregateRootId, paging.page(),
            paging.size()));
    }
}

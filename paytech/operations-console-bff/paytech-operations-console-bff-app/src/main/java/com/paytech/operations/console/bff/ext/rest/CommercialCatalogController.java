package com.paytech.operations.console.bff.ext.rest;

import static com.paytech.operations.console.bff.ext.rest.RiskListController.requireQueryKeys;

import java.util.Set;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.paytech.foundation.web.paging.PagingRequestBody;
import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.operations.console.bff.integration.CommercialConsoleQueryClient;
import com.paytech.operations.console.bff.service.model.CommercialChangeModels.BusinessChangeEntry;
import com.paytech.operations.console.bff.service.model.CommercialListModels.CatalogCapabilityListResponseBody;
import com.paytech.operations.console.bff.service.model.CommercialListModels.CatalogFeeEventListResponseBody;
import com.paytech.operations.console.bff.service.model.CommercialListModels.CatalogProductListResponseBody;
import com.paytech.operations.console.bff.service.processor.CommercialBusinessChangeProcessor;
import com.paytech.operations.console.bff.service.processor.request.CommercialBusinessChangeRequest;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;

/**
 * 商务产品目录与变更历史的运营入口（第二切片 I2，只读）
 * <li>Gateway 绑定：四个端点均为 COMMERCIAL_CONTRACT_READ（与产品目录、变更历史菜单一致）。</li>
 * <li>产品目录三表简单转发 commercial ProductCatalogApi（不分页）；变更历史经 Processor 为每条快照补充与上一快照的字段级比较。</li>
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

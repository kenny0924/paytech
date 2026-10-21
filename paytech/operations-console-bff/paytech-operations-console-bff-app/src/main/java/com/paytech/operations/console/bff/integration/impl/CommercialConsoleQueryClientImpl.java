package com.paytech.operations.console.bff.integration.impl;

import static com.paytech.operations.console.bff.integration.impl.CommercialContractConverter.convert;
import static com.paytech.operations.console.bff.integration.impl.CommercialContractConverter.require;

import java.util.List;
import java.util.Objects;

import org.springframework.stereotype.Component;

import com.paytech.commercial.api.agreement.AgreementQueryApi;
import com.paytech.commercial.api.catalog.ProductCatalogApi;
import com.paytech.commercial.api.history.BusinessChangeQueryApi;
import com.paytech.commercial.api.term.CommercialTermQueryApi;
import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.operations.console.bff.integration.CommercialConsoleQueryClient;
import com.paytech.operations.console.bff.service.model.CommercialChangeModels.BusinessChangeEntry;
import com.paytech.operations.console.bff.service.model.CommercialListModels.AgreementListItem;
import com.paytech.operations.console.bff.service.model.CommercialListModels.AgreementListRequestBody;
import com.paytech.operations.console.bff.service.model.CommercialListModels.CatalogCapabilityListResponseBody;
import com.paytech.operations.console.bff.service.model.CommercialListModels.CatalogDimensionListResponseBody;
import com.paytech.operations.console.bff.service.model.CommercialListModels.CatalogFeeEventListResponseBody;
import com.paytech.operations.console.bff.service.model.CommercialListModels.CatalogProductListResponseBody;
import com.paytech.operations.console.bff.service.model.CommercialListModels.TermPlanListItem;
import com.paytech.operations.console.bff.service.model.CommercialListModels.TermPlanListRequestBody;
import com.paytech.operations.console.bff.service.model.CommercialListModels.TermVersionListItem;
import com.paytech.operations.console.bff.service.model.CommercialListModels.TermVersionListRequestBody;
import com.paytech.operations.console.bff.service.model.CommercialPricingModels.CommercialTermTemplateListResponseBody;

import lombok.AllArgsConstructor;

/**
 * commercial 运营只读列表、产品目录、维度清单、标准价目模板与变更历史的 HTTP 转发
 * <li>列表行严格转换为 BFF 页面模型；分页回执的 page / size 与请求不一致、total 为负或条目为 null 视为上游无效响应（502）。</li>
 * <li>Owner 4xx 由开启 Owner Problem detail 的代理工厂转为带 Owner 原因的本地异常，本类不捕获。</li>
 *
 * @author Claude
 */
@Component
@AllArgsConstructor
public class CommercialConsoleQueryClientImpl implements CommercialConsoleQueryClient {
    private final ProductCatalogApi catalogApi;
    private final AgreementQueryApi agreementQueryApi;
    private final BusinessChangeQueryApi businessChangeApi;
    private final CommercialTermQueryApi termQueryApi;

    /** 合同分页。 */
    @Override
    public PagingResponseBody<AgreementListItem> listAgreements(AgreementListRequestBody request) {
        var page = agreementQueryApi.listAgreements(request.getClientId(), request.getLocalPartnerId(),
            request.getAgreementType(), emptyToNull(request.getStatus()), request.getEntityNo(), request.getAgreementNo(),
            request.getPage(), request.getSize());
        return page(page, request.getPage(), request.getSize(), AgreementListItem.class);
    }

    /** 方案分页。 */
    @Override
    public PagingResponseBody<TermPlanListItem> listPlans(TermPlanListRequestBody request) {
        var page = termQueryApi.listPlans(request.getAgreementId(), request.getClientId(), request.getLocalPartnerId(),
            request.getTermType(), request.getStatus(), request.getPage(), request.getSize());
        return page(page, request.getPage(), request.getSize(), TermPlanListItem.class);
    }

    /** 版本分页。 */
    @Override
    public PagingResponseBody<TermVersionListItem> listVersions(TermVersionListRequestBody request) {
        var page = termQueryApi.listVersions(request.getPlanId(), request.getAgreementId(), null,
            emptyToNull(request.getStatus()), request.getEffectiveFrom(), request.getEffectiveTo(), request.getPage(),
            request.getSize());
        return page(page, request.getPage(), request.getSize(), TermVersionListItem.class);
    }

    /** 模板方案列表；集合必须存在且每行为模板方案。 */
    @Override
    public CommercialTermTemplateListResponseBody listTemplates(String entityNo, String termType, String status) {
        var templates = convert(termQueryApi.listTemplates(entityNo, termType, status),
            CommercialTermTemplateListResponseBody.class);
        require(templates.getTemplates() != null && templates.getTemplates().stream()
            .allMatch(template -> template != null && template.getPlanId() != null && template.getEntityNo() != null));
        return templates;
    }

    /** 产品列表；集合必须存在。 */
    @Override
    public CatalogProductListResponseBody listProducts(String status) {
        var products = convert(catalogApi.listProducts(status), CatalogProductListResponseBody.class);
        require(products.getProducts() != null && !products.getProducts().contains(null));
        return products;
    }

    /** 可开通项列表；集合必须存在。 */
    @Override
    public CatalogCapabilityListResponseBody listCapabilities(String productCode, String status) {
        var capabilities = convert(catalogApi.listCapabilities(productCode, status), CatalogCapabilityListResponseBody.class);
        require(capabilities.getCapabilities() != null && !capabilities.getCapabilities().contains(null));
        return capabilities;
    }

    /** 计费事件列表；集合必须存在。 */
    @Override
    public CatalogFeeEventListResponseBody listFeeEvents(String productCode, String termType) {
        var feeEvents = convert(catalogApi.listFeeEvents(productCode, termType), CatalogFeeEventListResponseBody.class);
        require(feeEvents.getFeeEvents() != null && !feeEvents.getFeeEvents().contains(null));
        return feeEvents;
    }

    /** 维度清单；集合必须存在。 */
    @Override
    public CatalogDimensionListResponseBody listDimensions() {
        var dimensions = convert(catalogApi.listDimensions(), CatalogDimensionListResponseBody.class);
        require(dimensions.getDimensions() != null && !dimensions.getDimensions().contains(null));
        return dimensions;
    }

    /** 变更快照分页；每条快照必须带版本与审计视图。 */
    @Override
    public PagingResponseBody<BusinessChangeEntry> listChanges(String aggregateRootType, String aggregateRootId, int page,
        int size) {
        var changes = page(businessChangeApi.listChanges(aggregateRootType, aggregateRootId, page, size), page, size,
            BusinessChangeEntry.class);
        require(changes.items().stream().allMatch(entry -> entry.getVersionNo() != null && entry.getData() != null
            && entry.getRecordType() != null && entry.getRecordId() != null));
        return changes;
    }

    /** 分页回执核对后逐条严格转换，保留 page / size / total。 */
    static <T> PagingResponseBody<T> page(PagingResponseBody<?> owner, int page, int size, Class<T> type) {
        require(owner != null && owner.page() == page && owner.size() == size && owner.total() >= 0
            && owner.items().size() <= size && owner.items().stream().allMatch(Objects::nonNull));
        return owner.map(item -> convert(item, type));
    }

    /** 空状态列表等同不筛选，不发送空的重复参数。 */
    private static List<String> emptyToNull(List<String> values) {
        return values == null || values.isEmpty() ? null : values;
    }
}

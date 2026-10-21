package com.paytech.operations.console.bff.integration;

import com.paytech.foundation.web.paging.PagingResponseBody;
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

/**
 * commercial 运营只读列表、产品目录、维度清单、标准价目模板与变更历史的本地端口；筛选、排序、分页与授权事实归 commercial
 * <li>全部为查询，不携带操作人；每个入口只调用一次 commercial，不重试。</li>
 * <li>分页回执的 page / size 必须与请求一致，否则按上游无效响应处理。</li>
 *
 * @author Claude
 */
public interface CommercialConsoleQueryClient {

    /**
     * 合同分页
     *
     * @param request 筛选与分页
     * @return 合同分页，按创建时间倒序
     */
    PagingResponseBody<AgreementListItem> listAgreements(AgreementListRequestBody request);

    /**
     * 条款方案分页
     *
     * @param request 筛选与分页
     * @return 方案分页，含当前 ACTIVE 与已排期版本
     */
    PagingResponseBody<TermPlanListItem> listPlans(TermPlanListRequestBody request);

    /**
     * 条款版本分页
     *
     * @param request 筛选与分页
     * @return 版本分页；只筛 PENDING_APPROVAL 时按提交时刻升序
     */
    PagingResponseBody<TermVersionListItem> listVersions(TermVersionListRequestBody request);

    /**
     * 标准价目模板方案列表（不分页）
     *
     * @param entityNo 可选 OE
     * @param termType 可选条款类型
     * @param status 可选方案状态
     * @return 模板方案，含当前生效版本与最新版本
     */
    CommercialTermTemplateListResponseBody listTemplates(String entityNo, String termType, String status);

    /**
     * 产品（L0）列表
     *
     * @param status 可选状态
     * @return 产品列表
     */
    CatalogProductListResponseBody listProducts(String status);

    /**
     * 可开通项（L1）列表
     *
     * @param productCode 可选产品代码
     * @param status 可选状态
     * @return 可开通项列表
     */
    CatalogCapabilityListResponseBody listCapabilities(String productCode, String status);

    /**
     * 计费事件（L2）列表
     *
     * @param productCode 可选产品代码
     * @param termType 可选条款类型
     * @return 计费事件列表
     */
    CatalogFeeEventListResponseBody listFeeEvents(String productCode, String termType);

    /**
     * 维度清单（代码登记的维度元数据）
     *
     * @return 全部维度，按登记顺序
     */
    CatalogDimensionListResponseBody listDimensions();

    /**
     * 一个聚合根的变更快照分页，按聚合版本倒序；比较字段未填充
     *
     * @param aggregateRootType 聚合根类型
     * @param aggregateRootId 聚合根 ID
     * @param page 页码
     * @param size 每页条数
     * @return 快照分页；聚合根没有历史时为空页
     */
    PagingResponseBody<BusinessChangeEntry> listChanges(String aggregateRootType, String aggregateRootId, int page, int size);
}

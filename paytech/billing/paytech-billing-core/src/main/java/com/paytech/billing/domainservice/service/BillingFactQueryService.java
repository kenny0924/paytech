package com.paytech.billing.domainservice.service;

import com.paytech.billing.api.enums.FactRefType;
import com.paytech.billing.core.dal.dataobject.BillingFact;
import com.paytech.billing.domain.BillingFactBookScope;
import com.paytech.billing.domain.BillingFactSearchCriteria;
import com.paytech.billing.domain.view.BillingFactPageView;
import com.paytech.billing.domain.view.BillingFactProgressView;
import com.paytech.billing.domain.view.BillingFactView;
import com.paytech.foundation.web.paging.PagingRequestBody;
import com.paytech.foundation.web.paging.PagingResponseBody;

/**
 * 计费事实读取：按 ID、按账簿主体、按外部引用与发布进度
 * <li>只读，不开显式事务：各表读取允许观察相邻的已提交状态（事实内容登记后不变，只有状态、版本与发布时间推进）。</li>
 * <li>每条事实返回头、引用、明细行及其计算证据、定价输入与以其明细行为新行的行链接。</li>
 */
public interface BillingFactQueryService {

    /** 查询事实详情；不存在时业务拒绝。 */
    BillingFactView getFact(long factId);

    /** 账簿主体下 ID 大于游标的已批准事实，按 ID 升序；本页满 limit 条时以最后一条 ID 作为下一页游标。 */
    BillingFactPageView listRecordedFacts(BillingFactBookScope scope, long fromFactId, int limit);

    /** 引用给定外部对象且 ID 大于游标的任意状态事实，按 ID 升序；下一页游标规则同账簿列表。 */
    BillingFactPageView listFactsByRef(FactRefType refType, String refId, long fromFactId, int limit);

    /** 账簿主体下已批准事实的发布进度。 */
    BillingFactProgressView getPublicationProgress(BillingFactBookScope scope);

    /**
     * 管理列表：按 OE、币种与可选条件分页检索任意状态事实头，按 ID 降序
     *
     * @param criteria 筛选条件
     * @param paging 已校验的分页参数
     * @return 事实头分页，总额已按币种精度补齐；同一筛选条件下统计总数
     */
    PagingResponseBody<BillingFact> searchFacts(BillingFactSearchCriteria criteria, PagingRequestBody paging);
}

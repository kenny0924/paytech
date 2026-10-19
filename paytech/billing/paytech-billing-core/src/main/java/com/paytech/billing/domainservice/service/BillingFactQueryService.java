package com.paytech.billing.domainservice.service;

import com.paytech.billing.api.enums.FactRefType;
import com.paytech.billing.domain.BillingFactBookScope;
import com.paytech.billing.domain.view.BillingFactPageView;
import com.paytech.billing.domain.view.BillingFactProgressView;
import com.paytech.billing.domain.view.BillingFactView;

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
}

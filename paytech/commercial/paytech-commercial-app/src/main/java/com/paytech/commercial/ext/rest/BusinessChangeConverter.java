package com.paytech.commercial.ext.rest;

import static com.paytech.commercial.ext.rest.WireValues.code;
import static com.paytech.commercial.ext.rest.WireValues.id;
import static com.paytech.commercial.ext.rest.WireValues.text;

import com.paytech.commercial.api.history.response.BusinessChangeMeta;
import com.paytech.commercial.api.history.response.BusinessChangeSummary;
import com.paytech.commercial.domain.BusinessChangeEntryView;
import com.paytech.commercial.domain.BusinessChangeHistoryQuery;
import com.paytech.commercial.domain.enums.BusinessChangeRecordAggregateRootType;
import com.paytech.foundation.web.paging.PagingRequestBody;

/** 变更历史 Wire 与内部查询 / 结果的协议转换，不访问数据库或执行业务校验。 */
final class BusinessChangeConverter {
    /** 纯静态工具。 */
    private BusinessChangeConverter() {
    }

    /** 查询条件；两个必填参数由 Spring 绑定保证存在。 */
    static BusinessChangeHistoryQuery query(String aggregateRootType, String aggregateRootId, Integer page, Integer size) {
        return new BusinessChangeHistoryQuery(
            code(BusinessChangeRecordAggregateRootType.class, "aggregateRootType", aggregateRootType),
            id("aggregateRootId", aggregateRootId), PagingRequestBody.of(page, size));
    }

    /** 一条快照。 */
    static BusinessChangeSummary summary(BusinessChangeEntryView view) {
        BusinessChangeSummary summary = new BusinessChangeSummary();
        summary.setHistoryId(Long.toString(view.getHistoryId()));
        summary.setRecordType(text(view.getRecordType()));
        summary.setRecordId(view.getRecordId());
        summary.setVersionNo(view.getVersionNo());
        summary.setOperationType(text(view.getOperationType()));
        summary.setCreateBy(Long.toString(view.getCreateBy()));
        summary.setCreateTime(text(view.getCreateTime()));
        BusinessChangeMeta meta = new BusinessChangeMeta();
        meta.setTriggerType(view.getTriggerType());
        meta.setTriggerId(view.getTriggerId());
        meta.setReasonCode(view.getReasonCode());
        summary.setMeta(meta);
        summary.setData(view.getData());
        return summary;
    }
}

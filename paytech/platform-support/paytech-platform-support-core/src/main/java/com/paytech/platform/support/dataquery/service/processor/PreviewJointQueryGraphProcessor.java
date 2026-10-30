package com.paytech.platform.support.dataquery.service.processor;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.paytech.foundation.common.error.BizCheckFailException;
import com.paytech.foundation.processor.AbstractProcessor;
import com.paytech.platform.support.dataquery.core.configuration.DataQueryCallerProperties;
import com.paytech.platform.support.dataquery.domain.JointQueryGraph;
import com.paytech.platform.support.dataquery.domain.JointQueryPreview;
import com.paytech.platform.support.dataquery.domain.QueryKeyTypeDetail;
import com.paytech.platform.support.dataquery.domainservice.service.JointQueryGraphLoader;
import com.paytech.platform.support.dataquery.service.processor.request.PreviewJointQueryGraphRequest;

import lombok.AllArgsConstructor;

/**
 * 从入口键类型出发的可达预览（J2），不执行查询，无锁
 * <li>调用方必须在 data-query.http.callers 中；操作人会话需具备 DATA_QUERY_EXECUTE。</li>
 * <li>现读图；入口键类型缺失、不存在、非 ACTIVE 或不允许作为入口返回 400 BUSINESS_CHECK_FAILED（GET 没有请求体，
 * 不用 422 字段错误：内部 HTTP 客户端只为带请求体的方法映射字段错误）。不开事务、不写库。</li>
 */
@Component
@AllArgsConstructor
@ConditionalOnProperty(name = "operations.authentication.enabled", havingValue = "true")
public class PreviewJointQueryGraphProcessor
    extends AbstractProcessor<PreviewJointQueryGraphRequest, JointQueryPreview> {

    private final JointQueryGraphLoader loader;
    private final DataQueryCallerProperties callers;
    private final DataQueryManagementSupport support;

    @Override
    protected JointQueryPreview processRequest(PreviewJointQueryGraphRequest request) {
        // 调用方与操作人
        callers.authorize(request.getCallerService());
        support.requirePermission(request.getActor(), request.getSession(),
            DataQueryManagementSupport.EXECUTE_PERMISSION);

        // 现读图并推算
        JointQueryGraph graph = loader.loadGraph();
        requireEntry(graph, request.getEntryKeyType());
        return graph.preview(request.getEntryKeyType());
    }

    /** 入口键类型必须存在、ACTIVE 且允许作为入口。 */
    static void requireEntry(JointQueryGraph graph, String entryKeyType) {
        QueryKeyTypeDetail keyType = entryKeyType == null || entryKeyType.isBlank() ? null
            : graph.keyType(entryKeyType);
        if (keyType == null || !keyType.active() || !keyType.isEntryAllowed()) {
            throw new BizCheckFailException("入口键类型不存在、未启用或不允许作为入口: " + entryKeyType);
        }
    }
}

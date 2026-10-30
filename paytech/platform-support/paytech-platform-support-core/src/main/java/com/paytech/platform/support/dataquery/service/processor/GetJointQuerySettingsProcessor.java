package com.paytech.platform.support.dataquery.service.processor;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.paytech.foundation.processor.AbstractProcessor;
import com.paytech.platform.support.dataquery.core.configuration.DataQueryCallerProperties;
import com.paytech.platform.support.dataquery.domain.JointQuerySettings;
import com.paytech.platform.support.dataquery.domainservice.service.JointQueryGraphLoader;
import com.paytech.platform.support.dataquery.service.processor.request.GetJointQuerySettingsRequest;

import lombok.AllArgsConstructor;

/**
 * 联合查询页初始化数据（J1），无锁
 * <li>调用方必须在 data-query.http.callers 中；操作人会话需具备 DATA_QUERY_EXECUTE。</li>
 * <li>现读键类型，返回 ACTIVE 且允许作为入口的键类型、泳道分组与护栏；不开事务、不写库、不登记审计。</li>
 */
@Component
@AllArgsConstructor
@ConditionalOnProperty(name = "operations.authentication.enabled", havingValue = "true")
public class GetJointQuerySettingsProcessor extends AbstractProcessor<GetJointQuerySettingsRequest, JointQuerySettings> {

    private final JointQueryGraphLoader loader;
    private final DataQueryCallerProperties callers;
    private final DataQueryManagementSupport support;

    @Override
    protected JointQuerySettings processRequest(GetJointQuerySettingsRequest request) {
        callers.authorize(request.getCallerService());
        support.requirePermission(request.getActor(), request.getSession(),
            DataQueryManagementSupport.EXECUTE_PERMISSION);
        return loader.loadSettings();
    }
}

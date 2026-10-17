package com.paytech.platform.support.accesscontrol.service.processor;

import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import com.paytech.foundation.common.error.ErrorException;
import com.paytech.foundation.processor.AbstractProcessor;
import com.paytech.platform.support.accesscontrol.domain.gatewayconfiguration.GatewayConfigurationCompiler;
import com.paytech.platform.support.accesscontrol.domain.gatewayconfiguration.GatewayConfigurationProjection.LoadedConfiguration;
import com.paytech.platform.support.accesscontrol.domain.gatewayconfiguration.GatewaySnapshotView;
import com.paytech.platform.support.accesscontrol.domainservice.repository.GatewayConfigurationRepository;
import com.paytech.platform.support.accesscontrol.service.processor.request.QueryGatewaySnapshotRequest;

import lombok.AllArgsConstructor;

/** PLT-I20 查询入口：一致性读取完整配置，在数据库事务结束后编译有效快照。 */
@Component
@AllArgsConstructor
public class QueryGatewaySnapshotProcessor extends AbstractProcessor<QueryGatewaySnapshotRequest, GatewaySnapshotView> {

    private final TransactionTemplate transactionTemplate;
    private final GatewayConfigurationCompiler compiler;
    private final GatewayConfigurationRepository repository;

    /**
     * 读取同一提交时点的全部配置，结束事务后校验并编译完整结果。
     *
     * <p>
     * 复用只读 REPEATABLE READ、2 秒超时的查询事务模板，避免目标、路由及子配置来自不同提交时点。 编译失败直接交给公共异常出口，不返回部分目录，也不把故障伪装成关闭全部路由的空快照。
     */
    @Override
    protected GatewaySnapshotView processRequest(QueryGatewaySnapshotRequest request) {
        LoadedConfiguration loaded = transactionTemplate.execute(status -> repository.loadCurrentConfiguration());
        if (loaded == null) {
            throw new ErrorException("Gateway 当前配置读取未返回结果");
        }
        return compiler.compile(loaded);
    }
}

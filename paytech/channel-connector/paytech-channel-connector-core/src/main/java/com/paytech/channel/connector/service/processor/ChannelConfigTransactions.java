package com.paytech.channel.connector.service.processor;

import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import com.paytech.channel.connector.protocol.domain.ChannelConfigChangeRecord;
import com.paytech.channel.connector.protocol.domainservice.service.ChannelConfigChangeService;

import lombok.AllArgsConstructor;

/**
 * 渠道配置写入的本地原子保存：本模块业务写入与 protocol 的变更记录在同一事务中提交
 * <li>变更记录表归 protocol 模块，core 只经其公开的 {@link ChannelConfigChangeService} 写入；由 Processor 组合，Service 之间不互调。</li>
 * <li>事务内只访问本库；外部调用（如 Vault Protect）必须在调用前完成。</li>
 */
@Component
@AllArgsConstructor
public class ChannelConfigTransactions {
    private final TransactionTemplate transactionTemplate;
    private final ChannelConfigChangeService channelConfigChangeService;

    /**
     * 在一个本地事务中执行业务写入并写入变更记录
     *
     * @param change 已分配主键与创建时间的变更记录
     * @param businessWrite 本模块 Service 的本地写入；失败时整个事务回滚，变更记录不写入
     */
    void write(ChannelConfigChangeRecord change, Runnable businessWrite) {
        transactionTemplate.executeWithoutResult(status -> {
            businessWrite.run();
            channelConfigChangeService.record(change);
        });
    }
}

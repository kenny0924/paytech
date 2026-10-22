package com.paytech.platform.support.filedocument.ext.job;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.paytech.platform.support.filedocument.integration.storage.FileStorageRegistry;

import lombok.AllArgsConstructor;

/**
 * 进程内调度入口：按 files.storage.snapshot-refresh（默认 30 秒）重新加载存储快照
 * <li>只在 files.enabled=true 时装配；启动后立即加载一次。读取本库数据库，不访问 Vault 或存储。</li>
 */
@Component
@AllArgsConstructor
@ConditionalOnProperty(name = "files.enabled", havingValue = "true")
public class FileStorageSnapshotRefreshJob {

    private final FileStorageRegistry registry;

    /** 定时整体替换快照；失败保留上一份。 */
    @Scheduled(initialDelay = 0, fixedDelayString = "${files.storage.snapshot-refresh:PT30S}")
    public void refresh() {
        registry.refresh();
    }
}

package com.paytech.platform.support.test.base;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

import com.paytech.platform.support.test.dal.FileTestMapper;
import com.paytech.platform.support.test.mocker.external.StorageVaultMocker;
import com.paytech.platform.support.test.mocker.local.FileMocker;
import com.paytech.platform.support.test.mocker.local.StorageInstanceMocker;
import com.paytech.platform.support.test.mocker.local.StorageObjectMocker;

/**
 * 工作流附件的真实上传与下载测试基类：与文件测试使用同一组属性（files.enabled），复用同一个
 * Spring 上下文并连接仓库 local-environment 的 MinIO；存储实例、存储桶与默认写入路由每例由 {@link StorageInstanceMocker}
 * 插入，扫描策略由本基类每例插入。
 *
 * <p>本机 MinIO、Platform 账号或自签名证书缺失时在加载上下文前明确失败（与 {@link FileTestBase} 相同的检查）。
 * 每例结束先等待扫描任务结束、检查 Vault 意外调用，再由公共清理回收。</p>
 */
@TestPropertySource(properties = "files.enabled=true")
public abstract class WorkflowFileTestBase extends WorkflowApiTestBase {

    @Autowired
    protected StorageObjectMocker storage;

    @Autowired
    protected FileMocker fileMocker;

    @Autowired
    protected FileTestMapper fileData;

    @Autowired
    protected StorageInstanceMocker storageInstances;

    @Autowired
    protected StorageVaultMocker storageVault;

    /** 上下文加载前确认本机 MinIO 就绪；不启动或修改容器。 */
    @BeforeAll
    static void requireWorkflowMinio() {
        FileTestBase.requireLocalMinio();
    }

    /** 确认没有遗留的存储对象、扫描策略与 Vault 登记，再登记测试扫描策略、标准存储实例与默认路由。 */
    @BeforeEach
    void setUpWorkflowFileBase() {
        storage.assertEmpty();
        fileMocker.assertEmpty();
        storageVault.assertEmpty();
        fileMocker.formatPolicy();
        storageInstances.standard(FileMocker.POLICY_CODE);
    }

    /** 先等待扫描任务结束，再检查被业务捕获的 Vault 意外调用；公共清理随后执行。 */
    @AfterEach
    void tearDownWorkflowFileBase() {
        fileMocker.awaitScanTasks(platformDataCleaner);
        storageVault.assertNoUnexpectedCalls();
    }
}

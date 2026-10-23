package com.paytech.platform.support.test.base;

import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

import com.paytech.foundation.compensation.api.CompensationEventScanner;
import com.paytech.foundation.compensation.api.CompensationScanRequest;
import com.paytech.platform.support.api.enums.FileStorageBucketStatus;
import com.paytech.platform.support.filedocument.core.enums.FileAccessLevel;
import com.paytech.platform.support.filedocument.domain.file.FileSourceCodes;
import com.paytech.platform.support.localization.service.handler.ReleaseActivationHandler;
import com.paytech.platform.support.test.dal.LocalizationTestMapper;
import com.paytech.platform.support.test.localization.LocalizationMocker;
import com.paytech.platform.support.test.mocker.external.StorageVaultMocker;
import com.paytech.platform.support.test.mocker.local.FileMocker;
import com.paytech.platform.support.test.mocker.local.FileMocker.Policy;
import com.paytech.platform.support.test.mocker.local.StorageInstanceMocker;
import com.paytech.platform.support.test.mocker.local.StorageObjectMocker;
import com.paytech.platform.support.test.mocker.local.TempBucketMocker;

/**
 * 多语言入口测试的公共准备：导入文件真实写入本机 MinIO 并经生产读取链路读回，与文件测试使用同一组属性（files.enabled），
 * 复用同一个 Spring 上下文；存储实例、存储桶、默认路由与扫描策略每例插入，en 与 zh-CN 两个语言每例插入。
 * <li>本机 MinIO、Platform 账号或自签名证书缺失时在加载上下文前明确失败。</li>
 * <li>语言包经 Platform 文件入口写入公开桶：{@link #publicBundleBucket()} 登记本例临时公开桶（匿名可读）、管理员实例与
 * platform-support × PUBLIC 写入路由；上线由正式 Scanner / Dispatcher 执行（{@link #runActivations()}）。</li>
 * <li>每例结束先等待文件检查与上线任务停止执行，再检查 Vault 意外调用；公共清理随后执行。</li>
 */
@TestPropertySource(properties = "files.enabled=true")
public abstract class LocalizationApiTestBase extends OperationsApiTestBase {

    /** 接口前缀。 */
    protected static final String BASE = "/internal/localization/v1";

    @Autowired
    protected LocalizationMocker localization;

    @Autowired
    protected LocalizationTestMapper localizationRows;

    @Autowired
    protected StorageObjectMocker storage;

    @Autowired
    protected FileMocker fileMocker;

    @Autowired
    protected StorageInstanceMocker storageInstances;

    @Autowired
    protected StorageVaultMocker storageVault;

    @Autowired
    protected TempBucketMocker tempBuckets;

    @Autowired
    protected CompensationEventScanner scanner;

    /** 本例的测试检查策略（允许 PDF、PNG、JPEG、CSV 与 JSON，只要求格式检查）。 */
    protected Policy filePolicy;

    /** 上下文加载前确认本机 MinIO 就绪；不启动或修改容器。 */
    @BeforeAll
    static void requireLocalizationMinio() {
        FileTestBase.requireLocalMinio();
    }

    /** 确认存储与上线任务空基线后登记扫描策略、标准存储实例与默认路由，并插入 en、zh-CN 两个语言。 */
    @BeforeEach
    protected final void setUpLocalizationMocks() {
        storage.assertEmpty();
        fileMocker.assertEmpty();
        storageVault.assertEmpty();
        tempBuckets.assertEmpty();
        localization.assertNoActivationTasks();
        filePolicy = fileMocker.formatPolicy();
        storageInstances.standard(FileMocker.POLICY_CODE);
        localization.locales();
    }

    /** 等待文件检查与上线任务停止执行，再检查被业务捕获的 Vault 意外调用；公共清理随后执行。 */
    @AfterEach
    protected final void tearDownLocalizationMocks() {
        fileMocker.awaitScanTasks(platformDataCleaner);
        localization.awaitActivationIdle(platformDataCleaner);
        storageVault.assertNoUnexpectedCalls();
    }

    /** 应用下的路径。 */
    protected static String application(String applicationCode) {
        return BASE + "/applications/" + applicationCode;
    }

    /**
     * 语言包公开写入目标：本例临时公开桶（匿名可读）、可访问它的管理员实例与 platform-support × PUBLIC 写入路由（测试检查策略）。
     *
     * @return 公开桶的对外基础地址
     */
    protected String publicBundleBucket() {
        String bucket = tempBuckets.publicReadable();
        String baseUrl = tempBuckets.publicBaseUrl(bucket);
        storageInstances.rootInstance();
        storageInstances.publicBucket(StorageInstanceMocker.ROOT_INSTANCE, bucket, baseUrl,
            FileStorageBucketStatus.ACTIVE);
        storageInstances.route(FileSourceCodes.PLATFORM_SUPPORT, FileAccessLevel.PUBLIC,
            StorageInstanceMocker.ROOT_INSTANCE, bucket, FileMocker.POLICY_CODE);
        storageInstances.refresh();
        return baseUrl;
    }

    /**
     * 经正式 Scanner 与 Dispatcher 执行上线任务：先等文件检查与提交后的首次执行结束，再让仍在等待的任务立即到期并扫描一次，
     * 直到每个任务都已再执行一次（成功的任务被删除）。
     */
    protected void runActivations() {
        fileMocker.awaitScanTasks(platformDataCleaner);
        localization.awaitActivationIdle(platformDataCleaner);
        Map<String, Integer> before = localizationRows.activationTasks().stream().collect(Collectors.toMap(
            row -> (String) row.get("eventKey"), row -> ((Number) row.get("executeCount")).intValue()));
        localizationRows.activationDue();
        scanner.scan(new CompensationScanRequest(100, 0, 1, Set.of(ReleaseActivationHandler.EVENT_TYPE)));
        await().atMost(Duration.ofSeconds(15)).pollInterval(Duration.ofMillis(50)).until(() -> {
            Map<String, Map<String, Object>> current = localizationRows.activationTasks().stream()
                .collect(Collectors.toMap(row -> (String) row.get("eventKey"), row -> row));
            return before.entrySet().stream().allMatch(task -> {
                Map<String, Object> row = current.get(task.getKey());
                return row == null || (((Number) row.get("executeCount")).intValue() > task.getValue()
                    && !"PROCESSING".equals(row.get("executeStatus")));
            });
        });
    }
}

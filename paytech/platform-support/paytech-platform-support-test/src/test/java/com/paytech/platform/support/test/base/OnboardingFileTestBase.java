package com.paytech.platform.support.test.base;

import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.TestPropertySource;

import com.paytech.platform.support.filedocument.integration.storage.StorageTrust;
import com.paytech.platform.support.test.builder.OnboardingFileBuilder;
import com.paytech.platform.support.test.configuration.LocalMockEnvironmentInitializer;
import com.paytech.platform.support.test.dal.FileTestMapper;
import com.paytech.platform.support.test.mocker.external.CctFileTargetMocker;
import com.paytech.platform.support.test.mocker.external.StorageVaultMocker;
import com.paytech.platform.support.test.mocker.local.OnboardingApplicantMocker;
import com.paytech.platform.support.test.mocker.local.OnboardingApplicantMocker.Applicant;
import com.paytech.platform.support.test.mocker.local.OnboardingFileMocker;
import com.paytech.platform.support.test.mocker.local.StorageInstanceMocker;
import com.paytech.platform.support.test.mocker.local.StorageObjectMocker;
import com.paytech.platform.support.test.mocker.local.TempBucketMocker;

/**
 * 文件测试基类：在独立 Spring 上下文开启 files.enabled 与 files.onboarding.enabled 并连接仓库 local-environment 的 MinIO；
 * 共享 Mock 上下文保持关闭。
 *
 * <p>存储实例、存储桶与写入路由由 {@link StorageInstanceMocker} 每例插入：MINIO 类型与 S3 类型（AWS SDK）两个实例都指向本机
 * MinIO 的测试存储桶，默认路由指向 MINIO 实例；密钥经 Vault 测试接口返回。本机 MinIO、Platform 账号或自签名证书缺失时在加载上下文前
 * 明确失败并提示准备步骤。
 *
 * <p>上传开始使用本例插入的测试扫描策略；CCT 文件目标授权经真实出站 Client 访问独立 Mock HTTP 服务。
 * 每例结束先等待上传完成登记的扫描任务结束，再检查 CCT 意外调用，最后由公共清理回收。
 */
@TestPropertySource(properties = {"files.enabled=true", "files.onboarding.enabled=true"})
public abstract class OnboardingFileTestBase extends OperationsApiTestBase {

    /** 本机自签名证书，相对 local-environment；与 application-mock.yml 的 trusted-certificate 一致。 */
    private static final String CERTIFICATE = "minio/certs/public.crt";

    /** 本机 MinIO 存活检查地址；与 application-mock.yml 的 endpoint 一致。 */
    private static final String HEALTH = "https://127.0.0.1:19400/minio/health/live";

    private static final String SETUP =
        "；请在 local-environment 执行 `docker compose --env-file .env.local --profile storage up -d minio minio-init`，"
            + "并确认 .env.local 含 MinIO Platform 账号";

    @Autowired
    protected StorageObjectMocker storage;

    @Autowired
    protected OnboardingFileMocker fileMocker;

    @Autowired
    protected FileTestMapper fileData;

    @Autowired
    protected Clock clock;

    @Autowired
    protected CctFileTargetMocker cct;

    @Autowired
    protected OnboardingApplicantMocker applicants;

    @Autowired
    protected StorageInstanceMocker storageInstances;

    @Autowired
    protected StorageVaultMocker storageVault;

    @Autowired
    protected TempBucketMocker tempBuckets;

    /** 上下文加载前确认本机 MinIO、证书与 Platform 账号配置就绪（与 Mock 上下文同样读取 .env.local）；不启动或修改容器。 */
    @BeforeAll
    static void requireLocalMinio() {
        LocalMockEnvironmentInitializer local = new LocalMockEnvironmentInitializer();
        Path environment;
        try {
            environment = local.localEnvironment();
        } catch (IllegalStateException unresolved) {
            fail(unresolved.getMessage() + SETUP);
            return;
        }
        for (String key : new String[] {"MOR_MINIO_PLATFORM_ACCESS_KEY", "MOR_MINIO_PLATFORM_SECRET_KEY"}) {
            if (local.connectionValue(key) == null) {
                fail("缺少本地配置 " + key + SETUP);
            }
        }
        Path certificate = environment.resolve(CERTIFICATE);
        if (!Files.isRegularFile(certificate)) {
            fail("缺少本机 MinIO 证书 " + certificate.toAbsolutePath().normalize()
                + "；请先执行 local-environment/minio/generate-certs.sh" + SETUP);
        }
        HttpClient client = HttpClient.newBuilder().sslContext(pemTrust(certificate).context())
            .connectTimeout(Duration.ofSeconds(2)).build();
        try {
            int status =
                client.send(HttpRequest.newBuilder(URI.create(HEALTH)).timeout(Duration.ofSeconds(3)).GET().build(),
                    HttpResponse.BodyHandlers.discarding()).statusCode();
            if (status != 200) {
                fail("本机 MinIO 健康检查返回 " + status + SETUP);
            }
        } catch (IOException unavailable) {
            fail("本机 MinIO 不可连接（" + unavailable.getClass().getSimpleName() + "）" + SETUP);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            fail("本机 MinIO 健康检查被中断");
        }
    }

    /** 父类全局初始化后确认没有遗留的存储对象、测试扫描策略、CCT 与 Vault 登记，再登记标准存储实例与默认路由。 */
    @BeforeEach
    void setUpOnboardingFileBase() {
        storage.assertEmpty();
        fileMocker.assertEmpty();
        cct.assertEmpty();
        storageVault.assertEmpty();
        tempBuckets.assertEmpty();
        storageInstances.standard(OnboardingFileMocker.POLICY_CODE);
    }

    /** 先等待扫描任务结束，再检查被业务捕获的 CCT 与 Vault 意外调用；公共清理随后执行。 */
    @AfterEach
    void tearDownOnboardingFileBase() {
        fileMocker.awaitScanTasks(platformDataCleaner);
        cct.assertNoUnexpectedCalls();
        storageVault.assertNoUnexpectedCalls();
    }

    /** 读取 PEM 文件并复用生产信任材料。 */
    static StorageTrust pemTrust(Path certificate) {
        try {
            return StorageTrust.fromPemText(Files.readString(certificate));
        } catch (IOException failure) {
            throw new IllegalStateException("读取本机 MinIO 证书失败", failure);
        }
    }

    /** 应用固定时钟的当前微秒时间。 */
    protected Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }

    /** 以 Platform 账号写入唯一版本，再按该版本登记上传、文件与 Owner。 */
    protected OnboardingFileBuilder.Spec stored(OnboardingFileBuilder.Spec draft) {
        return fileMocker.file(draft.withVersion(storage.put(draft.objectRef(), draft.bytes())));
    }

    /** 申请人身份的可信 Header。 */
    protected HttpHeaders headers(Applicant applicant) {
        return headers(applicant.actor(), applicant.session());
    }

    /** 本例独有对象坐标上的默认 PDF 材料，提供方取当前上下文配置。 */
    protected OnboardingFileBuilder material(String provider) {
        return OnboardingFileBuilder.template(provider, storage.bucket(), storage.newObjectRef());
    }
}

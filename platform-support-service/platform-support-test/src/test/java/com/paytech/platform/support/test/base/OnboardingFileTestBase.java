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

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

import com.paytech.platform.support.filedocument.integration.storage.StorageTrust;
import com.paytech.platform.support.filedocument.integration.storage.minio.MinioFileStorage;
import com.paytech.platform.support.test.builder.OnboardingFileBuilder;
import com.paytech.platform.support.test.configuration.LocalMockEnvironmentInitializer;
import com.paytech.platform.support.test.dal.FileTestMapper;
import com.paytech.platform.support.test.mocker.local.OnboardingFileMocker;
import com.paytech.platform.support.test.mocker.local.StorageObjectMocker;

/**
 * 入驻文件测试基类：在独立 Spring 上下文开启文件能力并连接仓库 local-environment 的 MinIO；共享 Mock 上下文保持关闭。
 *
 * <p>
 * 默认存储提供方为 MINIO；子类可用 {@link TestPropertySource} 覆盖 {@code files.onboarding.storage-provider}。 本机 MinIO、Platform
 * 账号环境变量或自签名证书缺失时在加载上下文前明确失败并提示准备步骤。
 */
@TestPropertySource(
    properties = {"files.onboarding.enabled=true", "files.onboarding.storage-provider=" + MinioFileStorage.PROVIDER})
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

    /** 上下文加载前确认本机 MinIO、证书与 Platform 账号配置就绪（与 Mock 上下文同样读取 .env.local）；不启动或修改容器。 */
    @BeforeAll
    static void requireLocalMinio() {
        LocalMockEnvironmentInitializer local = new LocalMockEnvironmentInitializer();
        for (String key : new String[] {"MOR_MINIO_PLATFORM_ACCESS_KEY", "MOR_MINIO_PLATFORM_SECRET_KEY"}) {
            if (local.connectionValue(key) == null) {
                fail("缺少本地配置 " + key + SETUP);
            }
        }
        Path environment = local.localEnvironment();
        if (environment == null) {
            fail("无法定位仓库 local-environment" + SETUP);
        }
        Path certificate = environment.resolve(CERTIFICATE);
        if (!Files.isRegularFile(certificate)) {
            fail("缺少本机 MinIO 证书 " + certificate.toAbsolutePath().normalize()
                + "；请先执行 local-environment/minio/generate-certs.sh" + SETUP);
        }
        HttpClient client = HttpClient.newBuilder().sslContext(StorageTrust.fromPem(certificate).context())
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

    /** 父类全局初始化后确认没有遗留的存储对象与测试扫描策略。 */
    @BeforeEach
    void setUpOnboardingFileBase() {
        storage.assertEmpty();
        fileMocker.assertEmpty();
    }

    /** 应用固定时钟的当前微秒时间。 */
    protected Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }

    /** 以 Platform 账号写入唯一版本，再按该版本登记上传、文件与 Owner。 */
    protected OnboardingFileBuilder.Spec stored(OnboardingFileBuilder.Spec draft) {
        return fileMocker.file(draft.withVersion(storage.put(draft.objectRef(), draft.bytes())));
    }

    /** 本例独有对象坐标上的默认 PDF 材料，提供方取当前上下文配置。 */
    protected OnboardingFileBuilder material(String provider) {
        return OnboardingFileBuilder.template(provider, storage.bucket(), storage.newObjectRef());
    }
}

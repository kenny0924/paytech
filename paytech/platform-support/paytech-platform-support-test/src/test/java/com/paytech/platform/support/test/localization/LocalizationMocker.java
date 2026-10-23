package com.paytech.platform.support.test.localization;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.List;

import org.springframework.boot.test.context.TestComponent;

import com.paytech.foundation.testsupport.api.Clear;
import com.paytech.platform.support.api.enums.FileScanStatus;
import com.paytech.platform.support.localization.core.enums.KeyStatus;
import com.paytech.platform.support.localization.core.enums.LocaleStatus;
import com.paytech.platform.support.localization.domain.LocalizationCodes;
import com.paytech.platform.support.test.builder.OperatorBuilder;
import com.paytech.platform.support.test.dal.LocalizationTestMapper;
import com.paytech.platform.support.test.mocker.local.PlatformDataCleaner;
import com.paytech.platform.support.test.mocker.local.StorageObjectMocker;

import lombok.RequiredArgsConstructor;

/**
 * 多语言本地前置数据：语言、应用、命名空间、key、翻译，以及写入本机 MinIO 的导入文件。
 * <li>没有可变状态；七张表与文件两表由 PlatformDataCleaner 整表回收，MinIO 对象由 StorageObjectMocker 回收。</li>
 * <li>发布登记的上线补偿任务 I18N_RELEASE_ACTIVATE 不在整表回收范围：每例开始时要求为空，结束时等待执行停止后按类型删除。</li>
 */
@TestComponent
@RequiredArgsConstructor
public class LocalizationMocker implements Clear {

    /** 测试应用编码。 */
    public static final String APPLICATION = "client-console";

    /** 测试目标语言。 */
    public static final String ZH_CN = "zh-CN";

    private final Clock clock;
    private final LocalizationTestMapper mapper;
    private final StorageObjectMocker storage;

    /** 两个 ENABLED 语言：en（排序 0）与 zh-CN（排序 10）。 */
    public void locales() {
        locale(LocalizationCodes.BASE_LOCALE, "English", LocaleStatus.ENABLED, 0);
        locale(ZH_CN, "简体中文", LocaleStatus.ENABLED, 10);
    }

    /**
     * 插入语言。
     *
     * @param code 语言代码
     * @param nativeName 自称
     * @param status 状态
     * @param sortOrder 排序
     */
    public void locale(String code, String nativeName, LocaleStatus status, int sortOrder) {
        assertEquals(1, mapper.insertLocale(OperatorBuilder.nextId(), code, nativeName, nativeName, status.getCode(),
            sortOrder, now()));
    }

    /**
     * 插入应用：默认语言 en，必须覆盖给定语言。
     *
     * @param code 应用编码
     * @param threshold 覆盖率阈值
     * @param requiredLocales 必须覆盖语言
     * @return 应用 ID
     */
    public long application(String code, int threshold, String... requiredLocales) {
        long id = OperatorBuilder.nextId();
        String json = "[" + String.join(",", List.of(requiredLocales).stream().map(locale -> "\"" + locale + "\"")
            .toList()) + "]";
        assertEquals(1, mapper.insertApplication(id, code, LocalizationCodes.BASE_LOCALE, json, threshold, now()));
        return id;
    }

    /** 插入命名空间，返回 ID。 */
    public long namespace(long applicationId, String code) {
        long id = OperatorBuilder.nextId();
        assertEquals(1, mapper.insertNamespace(id, applicationId, code, 0, now()));
        return id;
    }

    /** 插入 ACTIVE key（基准修订号 1），返回 ID。 */
    public long key(long applicationId, long namespaceId, String code, String text) {
        return key(applicationId, namespaceId, code, text, KeyStatus.ACTIVE, 1);
    }

    /** 插入 key，返回 ID。 */
    public long key(long applicationId, long namespaceId, String code, String text, KeyStatus status,
        int sourceRevision) {
        long id = OperatorBuilder.nextId();
        assertEquals(1, mapper.insertKey(id, applicationId, namespaceId, code, text, sourceRevision, status.getCode(),
            now()));
        return id;
    }

    /** 插入翻译（版本 1），返回 ID。 */
    public long translation(long keyId, String locale, String text, int basedOnSourceRevision) {
        long id = OperatorBuilder.nextId();
        assertEquals(1, mapper.insertTranslation(id, keyId, locale, text, basedOnSourceRevision, now()));
        return id;
    }

    /**
     * 写入一个导入文件：对象放入本机 MinIO 测试存储桶，登记已完成上传与文件对象。
     *
     * @param reason 上传原因码
     * @param json 文件内容
     * @param scanStatus 扫描状态
     * @return 文件 ID
     */
    public long importFile(String reason, String json, FileScanStatus scanStatus) {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        long uploadId = OperatorBuilder.nextId();
        long fileId = OperatorBuilder.nextId();
        long policyId = OperatorBuilder.nextId();
        String objectRef = storage.newObjectRef();
        String version = storage.put(objectRef, bytes);
        Instant now = now();
        assertEquals(1, mapper.insertUpload(uploadId, reason, bytes.length, storage.bucket(), objectRef, policyId,
            now));
        assertEquals(1, mapper.insertFile(fileId, uploadId, reason, storage.bucket(), objectRef, version,
            sha256(bytes), bytes.length, policyId, scanStatus.getCode(), now));
        return fileId;
    }

    /** 每例开始时没有遗留的上线补偿任务。 */
    public void assertNoActivationTasks() {
        assertTrue(mapper.activationTasks().isEmpty(), "存在残留的多语言上线补偿任务");
    }

    /**
     * 有界等待上线补偿任务停止执行；超时则标记环境不可复用，避免后台继续写回已清空的表。
     *
     * @param cleaner 公共数据回收组件
     */
    public void awaitActivationIdle(PlatformDataCleaner cleaner) {
        try {
            await().atMost(Duration.ofSeconds(15)).pollInterval(Duration.ofMillis(50))
                .until(() -> mapper.processingActivations() == 0);
        } catch (RuntimeException | AssertionError timeout) {
            cleaner.markUnusable();
            throw timeout;
        }
    }

    /** 删除本例发布登记的上线补偿任务。 */
    @Override
    public void clear() {
        mapper.deleteActivationTasks();
    }

    /** 与被测链路相同的时钟，截断到数据库微秒精度。 */
    Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }

    /** 小写十六进制 SHA-256。 */
    public static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException unavailable) {
            throw new IllegalStateException(unavailable);
        }
    }
}

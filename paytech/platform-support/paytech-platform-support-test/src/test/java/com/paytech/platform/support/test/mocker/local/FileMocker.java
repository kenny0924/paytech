package com.paytech.platform.support.test.mocker.local;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.test.context.TestComponent;

import com.paytech.foundation.testsupport.api.Clear;
import com.paytech.platform.support.api.enums.FileScanStatus;
import com.paytech.platform.support.filedocument.domain.enums.FileScanType;
import com.paytech.platform.support.filedocument.integration.scanning.FormatFileScanner;
import com.paytech.platform.support.service.handler.FilePublicSyncHandler;
import com.paytech.platform.support.service.handler.FileScanHandler;
import com.paytech.platform.support.test.builder.FileBuilder;
import com.paytech.platform.support.test.builder.FileBuilder.Spec;
import com.paytech.platform.support.test.builder.OperatorBuilder;
import com.paytech.platform.support.test.dal.FileTestMapper;

import lombok.RequiredArgsConstructor;

/**
 * 文件的本地前置数据：扫描策略、未完成或已完成上传与文件对象。
 *
 * <p>上传、文件与扫描结果由 {@link PlatformDataCleaner} 整表回收；扫描策略表可能保留已授权的本机开发配置，
 * 本组件只登记并删除本例插入的策略及其要求。
 */
@TestComponent
@RequiredArgsConstructor
public class FileMocker implements Clear {

    /** 测试策略代码。 */
    public static final String POLICY_CODE = "UNIT_TEST_FILE";

    /** 格式检查最低运行版本。 */
    public static final String FORMAT_MINIMUM_VERSION = "1";

    /** 经检查策略管理接口创建的测试策略代码前缀；本组件按前缀回收。 */
    public static final String MANAGED_POLICY_PREFIX = "UT_MGMT_";

    /** 测试策略允许的类型规则。 */
    static final String MIME_RULES =
        "{\"mimeTypes\":[\"application/pdf\",\"image/png\",\"image/jpeg\",\"text/csv\",\"application/json\"]}";

    /** 本组件等待与回收的补偿任务类型。 */
    static final List<String> TASK_TYPES = List.of(FileScanHandler.EVENT_TYPE, FilePublicSyncHandler.EVENT_TYPE);

    private final java.time.Clock clock;
    private final FileTestMapper mapper;
    private final List<Long> policyIds = new ArrayList<>();

    /**
     * 插入只含一项 FORMAT 要求的 ACTIVE 策略：允许 PDF、PNG、JPEG、CSV 与 JSON，检查器 PLATFORM_FORMAT，只接受 FORMAT_VALID。
     *
     * @return 策略与要求标识
     */
    public Policy formatPolicy() {
        Instant now = now();
        long policyId = OperatorBuilder.nextId();
        long requirementId = OperatorBuilder.nextId();
        assertEquals(1, mapper.insertPolicy(policyId, POLICY_CODE, MIME_RULES, 10_485_760L, now));
        policyIds.add(policyId);
        assertEquals(1, mapper.insertRequirement(requirementId, policyId, 1, FormatFileScanner.SCANNER_CODE,
            FileScanType.FORMAT.getCode(), FORMAT_MINIMUM_VERSION, "[\"" + FormatFileScanner.VALID + "\"]", now));
        return new Policy(policyId, requirementId);
    }

    /**
     * 停用测试策略的当前修订，并以新的 ACTIVE 修订替代：只含一项指定检查器、类型与接受结果的要求
     * <li>例如接受结果不包含 FORMAT_VALID 时，格式检查通过的内容也被拒绝；检查器不存在时候选版本停在检查中。</li>
     *
     * @param current 当前 ACTIVE 修订
     * @param revisionNo 新修订号
     * @param scannerCode 检查器代码
     * @param scanType 检查类型
     * @param acceptedCode 唯一接受的结果代码
     * @return 新修订
     */
    public Policy replaceRevision(Policy current, int revisionNo, String scannerCode, String scanType,
        String acceptedCode) {
        Instant now = now();
        assertEquals(1, mapper.retirePolicy(current.policyId()));
        long policyId = OperatorBuilder.nextId();
        long requirementId = OperatorBuilder.nextId();
        assertEquals(1, mapper.insertPolicyRevision(policyId, POLICY_CODE, revisionNo, MIME_RULES, 10_485_760L,
            "ACTIVE", 1, now));
        policyIds.add(policyId);
        assertEquals(1, mapper.insertRequirement(requirementId, policyId, 1, scannerCode, scanType,
            FORMAT_MINIMUM_VERSION, "[\"" + acceptedCode + "\"]", now));
        return new Policy(policyId, requirementId);
    }

    /**
     * 直接插入一条替换上传
     *
     * @param file 被替换的文件
     * @param replacementNo 替换序号
     * @param baseVersion 开始替换时存储的最新版本
     * @param status 上传状态 Code
     * @param expiresAt 上传到期时间
     * @return 上传 ID
     */
    public long replacement(Spec file, int replacementNo, String baseVersion, String status, Instant expiresAt) {
        long uploadId = OperatorBuilder.nextId();
        assertEquals(1, mapper.insertReplacement(uploadId, file.fileId(), file.accessLevel().getCode(), file.size(),
            file.mime(), file.provider(), file.container(), file.objectRef(), replacementNo, baseVersion,
            file.policyId(), expiresAt, status, now()));
        return uploadId;
    }

    /**
     * 直接插入一条版本行
     *
     * @param file 所属文件
     * @param versionNo 版本号
     * @param uploadId 产生版本的上传，可空
     * @param objectVersion 存储 versionId
     * @param bytes 该版本的实际字节
     * @param mime 该版本登记的媒体类型
     * @param policyId 冻结的策略修订
     * @param scanStatus 扫描状态
     * @param versionStatus 版本状态 Code
     */
    public void version(Spec file, int versionNo, Long uploadId, String objectVersion, byte[] bytes, String mime,
        long policyId, FileScanStatus scanStatus, String versionStatus) {
        assertEquals(1, mapper.insertVersion(OperatorBuilder.nextId(), file.fileId(), versionNo, uploadId,
            objectVersion, FileBuilder.sha256(bytes), bytes.length, mime, policyId, scanStatus.getCode(),
            versionStatus, now()));
    }

    /** 插入尚未完成的上传目标；未指定期限时取当前时间后 900 秒。 */
    public Spec openUpload(Spec spec) {
        Instant now = now();
        Instant expiresAt = spec.expiresAt() == null ? now.plusSeconds(900) : spec.expiresAt();
        assertEquals(1, mapper.insertOpenUpload(spec.uploadId(), spec.tenantId(), spec.uploaderId(), spec.size(),
            spec.mime(), spec.displayName(), spec.provider(), spec.container(), spec.objectRef(), spec.policyId(),
            expiresAt, now));
        return spec;
    }

    /**
     * 有界等待本例上传完成后登记的扫描任务结束；超时则标记环境不可复用，避免后台继续写回已清空的表
     *
     * @param cleaner 公共数据回收组件
     */
    public void awaitScanTasks(PlatformDataCleaner cleaner) {
        try {
            await().atMost(Duration.ofSeconds(15)).pollInterval(Duration.ofMillis(50))
                .until(() -> TASK_TYPES.stream().allMatch(type -> mapper.activeTasks(type) == 0));
        } catch (RuntimeException | AssertionError timeout) {
            cleaner.markUnusable();
            throw timeout;
        }
    }

    /** 插入已完成上传、对应文件对象与 v1 版本行（与回填 DML 一致）。 */
    public Spec file(Spec spec) {
        Instant now = now();
        assertEquals(1, mapper.insertUpload(spec.uploadId(), spec.tenantId(), spec.uploaderId(), spec.size(),
            spec.mime(), spec.displayName(), spec.provider(), spec.container(), spec.objectRef(), spec.policyId(),
            now.plusSeconds(900), now));
        Instant availableTime = spec.scanStatus() == FileScanStatus.AVAILABLE ? now : null;
        assertEquals(1, mapper.insertFile(spec.fileId(), spec.uploadId(), spec.accessLevel().getCode(), spec.provider(),
            spec.container(),
            spec.objectRef(), spec.version(), spec.contentHash(), spec.size(), spec.mime(), spec.encryption(),
            spec.policyId(), spec.scanStatus().getCode(), spec.objectStatus().getCode(), availableTime, now));
        assertEquals(1, mapper.insertVersionOne(spec.fileId()));
        return spec;
    }

    /** 每例开始时没有遗留的策略登记，库中也没有上次中断残留的测试策略。 */
    public void assertEmpty() {
        assertAll(() -> assertTrue(policyIds.isEmpty(), "上一例测试扫描策略未回收"),
            () -> assertEquals(0, mapper.countPolicies(POLICY_CODE),
                "存在上次中断残留的测试扫描策略 " + POLICY_CODE + "，请人工确认后删除"),
            () -> assertEquals(0, mapper.countPoliciesWithPrefix(MANAGED_POLICY_PREFIX),
                "存在上次中断残留的测试检查策略 " + MANAGED_POLICY_PREFIX + "*，请人工确认后删除"),
            () -> assertTrue(TASK_TYPES.stream().allMatch(type -> mapper.taskKeys(type).isEmpty()),
                "存在残留的文件扫描或公开同步任务"));
    }

    /** 删除本例插入或经管理接口创建的测试策略及其要求、本例产生的扫描与公开同步任务，不触碰本机开发配置。 */
    @Override
    public void clear() {
        try {
            TASK_TYPES.forEach(mapper::deleteTasks);
            mapper.deleteRequirementsWithPrefix(MANAGED_POLICY_PREFIX);
            mapper.deletePoliciesWithPrefix(MANAGED_POLICY_PREFIX);
            for (Long policyId : policyIds) {
                mapper.deleteRequirements(policyId);
                mapper.deletePolicy(policyId);
            }
        } finally {
            policyIds.clear();
        }
    }

    /** 与被测链路相同的固定时钟，截断到数据库微秒精度。 */
    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }

    /**
     * 已插入的扫描策略。
     *
     * @param policyId 策略修订
     * @param requirementId FORMAT 要求
     */
    public record Policy(long policyId, long requirementId) {
    }
}

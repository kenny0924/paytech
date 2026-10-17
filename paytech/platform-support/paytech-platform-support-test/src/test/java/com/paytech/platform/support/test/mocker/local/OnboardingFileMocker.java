package com.paytech.platform.support.test.mocker.local;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.test.context.TestComponent;

import com.paytech.foundation.testsupport.api.Clear;
import com.paytech.platform.support.api.enums.FileScanStatus;
import com.paytech.platform.support.filedocument.domain.enums.FileScanType;
import com.paytech.platform.support.filedocument.integration.scanning.FormatFileScanner;
import com.paytech.platform.support.test.builder.OnboardingFileBuilder.Spec;
import com.paytech.platform.support.test.builder.OperatorBuilder;
import com.paytech.platform.support.test.dal.FileTestMapper;

import lombok.RequiredArgsConstructor;

/**
 * 入驻文件的本地前置数据：扫描策略、已完成上传、文件对象与 CCT Owner 引用。
 *
 * <p>上传、文件、Owner 与结果由 {@link PlatformDataCleaner} 整表回收；扫描策略表可能保留已授权的本机开发配置，
 * 本组件只登记并删除本例插入的策略及其要求。
 */
@TestComponent
@RequiredArgsConstructor
public class OnboardingFileMocker implements Clear {

    /** 测试策略代码。 */
    public static final String POLICY_CODE = "UNIT_TEST_ONBOARDING";

    /** 格式检查最低运行版本。 */
    public static final String FORMAT_MINIMUM_VERSION = "1";

    private final java.time.Clock clock;
    private final FileTestMapper mapper;
    private final List<Long> policyIds = new ArrayList<>();

    /**
     * 插入只含一项 FORMAT 要求的 ACTIVE 策略：检查器 PLATFORM_FORMAT，只接受 FORMAT_VALID。
     *
     * @return 策略与要求标识
     */
    public Policy formatPolicy() {
        Instant now = now();
        long policyId = OperatorBuilder.nextId();
        long requirementId = OperatorBuilder.nextId();
        assertEquals(1, mapper.insertPolicy(policyId, POLICY_CODE,
            "[\"application/pdf\",\"image/png\",\"image/jpeg\"]", 10_485_760L, now));
        policyIds.add(policyId);
        assertEquals(1, mapper.insertRequirement(requirementId, policyId, 1, FormatFileScanner.SCANNER_CODE,
            FileScanType.FORMAT.getCode(), FORMAT_MINIMUM_VERSION, "[\"" + FormatFileScanner.VALID + "\"]", now));
        return new Policy(policyId, requirementId);
    }

    /** 插入已完成上传、对应文件对象及入驻材料 Owner 引用。 */
    public Spec file(Spec spec) {
        Instant now = now();
        assertEquals(1, mapper.insertUpload(spec.uploadId(), spec.tenantId(), spec.uploaderId(), spec.size(),
            spec.mime(), spec.ownerRefId(), spec.displayName(), spec.provider(), spec.container(), spec.objectRef(),
            spec.policyId(), now.plusSeconds(900), now));
        Instant availableTime = spec.scanStatus() == FileScanStatus.AVAILABLE ? now : null;
        assertEquals(1, mapper.insertFile(spec.fileId(), spec.uploadId(), spec.provider(), spec.container(),
            spec.objectRef(), spec.version(), spec.contentHash(), spec.size(), spec.mime(), spec.encryption(),
            spec.policyId(), spec.scanStatus().getCode(), spec.objectStatus().getCode(), availableTime, now));
        assertEquals(1, mapper.insertOwner(OperatorBuilder.nextId(), spec.fileId(), spec.ownerRefId(), now));
        return spec;
    }

    /** 每例开始时没有遗留的策略登记，库中也没有上次中断残留的测试策略。 */
    public void assertEmpty() {
        assertAll(() -> assertTrue(policyIds.isEmpty(), "上一例测试扫描策略未回收"),
            () -> assertEquals(0, mapper.countPolicies(POLICY_CODE),
                "存在上次中断残留的测试扫描策略 " + POLICY_CODE + "，请人工确认后删除"));
    }

    /** 删除本例插入的策略及其要求，不触碰本机开发配置。 */
    @Override
    public void clear() {
        try {
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

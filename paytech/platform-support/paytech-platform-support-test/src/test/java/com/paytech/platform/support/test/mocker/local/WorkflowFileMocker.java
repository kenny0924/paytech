package com.paytech.platform.support.test.mocker.local;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.springframework.boot.test.context.TestComponent;

import com.paytech.platform.support.api.enums.FileScanStatus;
import com.paytech.platform.support.test.builder.OperatorBuilder;
import com.paytech.platform.support.test.dal.WorkflowFileTestMapper;
import com.paytech.platform.support.workflow.domainservice.validator.AttachmentRules;

import lombok.RequiredArgsConstructor;

/**
 * 工作流附件的本地文件前置数据：按用例指定的上传人、来源、原因、类型与扫描状态插入已完成的上传与文件对象。
 * 没有可变状态；两表由 {@link PlatformDataCleaner} 整表回收。
 */
@TestComponent
@RequiredArgsConstructor
public class WorkflowFileMocker {

    /** 测试文件的大小。 */
    public static final long SIZE = 2048;

    /** 未指定存储桶时登记的存储桶名；不对应真实存储桶，只用于提交校验与重新验证场景。 */
    public static final String UNSTORED_CONTAINER = "workflow-test";

    /** 默认的 PDF 类型。 */
    public static final String PDF = "application/pdf";

    private final Clock clock;
    private final WorkflowFileTestMapper mapper;

    /**
     * 插入一份工作流附件（platform-support / WORKFLOW_ATTACHMENT）。
     *
     * @param uploaderId 上传人
     * @param scanStatus 扫描状态
     * @param mime 媒体类型
     * @return 文件 ID
     */
    public long attachment(long uploaderId, FileScanStatus scanStatus, String mime) {
        return file(uploaderId, AttachmentRules.SOURCE_SYSTEM, AttachmentRules.REASON_CODE, scanStatus, mime);
    }

    /**
     * 插入一份已扫描通过的 PDF 工作流附件。
     *
     * @param uploaderId 上传人
     * @return 文件 ID
     */
    public long available(long uploaderId) {
        return attachment(uploaderId, FileScanStatus.AVAILABLE, PDF);
    }

    /**
     * 插入任意来源与原因的文件。
     *
     * @param uploaderId 上传人
     * @param source 来源系统
     * @param reason 上传原因
     * @param scanStatus 扫描状态
     * @param mime 媒体类型
     * @return 文件 ID
     */
    public long file(long uploaderId, String source, String reason, FileScanStatus scanStatus, String mime) {
        return file(uploaderId, source, reason, scanStatus, mime, UNSTORED_CONTAINER);
    }

    /**
     * 插入一份登记在 UT_MINIO 实例指定存储桶上的已扫描通过工作流附件，可签发下载地址（不放对象字节）。
     *
     * @param uploaderId 上传人
     * @param mime 媒体类型
     * @param container 存储桶名
     * @return 文件 ID
     */
    public long stored(long uploaderId, String mime, String container) {
        return file(uploaderId, AttachmentRules.SOURCE_SYSTEM, AttachmentRules.REASON_CODE, FileScanStatus.AVAILABLE,
            mime, container);
    }

    /** 插入任意来源、原因与存储桶的文件。 */
    private long file(long uploaderId, String source, String reason, FileScanStatus scanStatus, String mime,
        String container) {
        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        long uploadId = OperatorBuilder.nextId();
        long fileId = OperatorBuilder.nextId();
        String objectRef = "workflow/" + fileId;
        long policyId = OperatorBuilder.nextId();
        assertEquals(1, mapper.insertUpload(uploadId, uploaderId, source, reason, SIZE, mime, "附件-" + fileId + ".pdf",
            container, objectRef, policyId, now));
        assertEquals(1, mapper.insertFile(fileId, uploadId, source, reason, SIZE, mime, container, objectRef,
            policyId, scanStatus.getCode(), now));
        return fileId;
    }

    /**
     * 模拟扫描结束。
     *
     * @param fileId 文件 ID
     * @param scanStatus 新扫描状态
     */
    public void scanned(long fileId, FileScanStatus scanStatus) {
        assertEquals(1, mapper.updateScanStatus(fileId, scanStatus.getCode()));
    }
}

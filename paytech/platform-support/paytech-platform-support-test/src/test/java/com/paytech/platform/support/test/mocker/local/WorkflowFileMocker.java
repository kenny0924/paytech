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
        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        long uploadId = OperatorBuilder.nextId();
        long fileId = OperatorBuilder.nextId();
        String objectRef = "workflow/" + fileId;
        long policyId = OperatorBuilder.nextId();
        assertEquals(1, mapper.insertUpload(uploadId, uploaderId, source, reason, SIZE, mime, "附件-" + fileId + ".pdf",
            objectRef, policyId, now));
        assertEquals(1, mapper.insertFile(fileId, uploadId, source, reason, SIZE, mime, objectRef, policyId,
            scanStatus.getCode(), now));
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

package com.paytech.platform.support.ext.rest.filestorage;

import java.time.Instant;

import org.slf4j.MDC;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.paytech.foundation.common.enums.CodeEnum;
import com.paytech.foundation.common.error.InvalidParameterException;
import com.paytech.foundation.common.utils.EnumUtils;
import com.paytech.foundation.httpproblem.api.ApiProblemProfile;
import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.platform.support.api.enums.FileScanStatus;
import com.paytech.platform.support.api.files.FileAddress;
import com.paytech.platform.support.api.files.response.FileDownloadResponseBody;
import com.paytech.platform.support.api.filestorage.response.FileRecordDetailResponseBody;
import com.paytech.platform.support.api.filestorage.response.FileRecordSummary;
import com.paytech.platform.support.api.filestorage.response.FileScanRecord;
import com.paytech.platform.support.api.filestorage.response.FileUploadRecord;
import com.paytech.platform.support.api.filestorage.response.FileVersionRecord;
import com.paytech.platform.support.filedocument.core.enums.FileAccessLevel;
import com.paytech.platform.support.filedocument.domain.file.FileRecordDetailView;
import com.paytech.platform.support.filedocument.domain.file.FileRecordView;
import com.paytech.platform.support.service.processor.files.response.AuthorizeFileDownloadResponse;
import com.paytech.platform.support.service.processor.filestorage.request.FileRecordRequest;
import com.paytech.platform.support.service.processor.filestorage.request.ListFileRecordsRequest;

/**
 * 文件记录的协议转换：ID 与字节数在 Wire 上是十进制字符串，Code 按稳定编码解析
 * <li>不认识的访问级别、扫描状态或不规范的文件 ID 返回 400；时间范围规则由 Processor 校验。</li>
 */
@Component
@ConditionalOnProperty(name = "operations.authentication.enabled", havingValue = "true")
public class FileRecordConverter {

    private static final String CANONICAL_ID = "[1-9][0-9]{0,18}";

    /** 无请求 ID 时写入审计的占位值（正常经 RequestIdFilter 总是存在）。 */
    private static final String UNKNOWN_REQUEST = "UNKNOWN";

    /** 列表查询：可选条件为空时不限制。 */
    ListFileRecordsRequest list(String actor, String session, Instant createdFrom, Instant createdTo, String fileId,
        String sourceSystem, String reasonCode, String accessLevel, String scanStatus, Boolean hasPendingReplacement,
        Integer page, Integer size) {
        return new ListFileRecordsRequest(actor, session, createdFrom, createdTo,
            fileId == null ? null : id(fileId), blankToNull(sourceSystem), blankToNull(reasonCode),
            code(FileAccessLevel.class, accessLevel, "accessLevel 只能为 PRIVATE 或 PUBLIC"),
            code(FileScanStatus.class, scanStatus, "scanStatus 不是有效的扫描状态"), hasPendingReplacement, page, size);
    }

    /** 详情查询。 */
    FileRecordRequest detail(String actor, String session, String fileId) {
        return new FileRecordRequest(actor, session, id(fileId), null, requestId());
    }

    /** 版本下载：请求 ID 取 MDC 供审计。 */
    FileRecordRequest download(String actor, String session, String fileId, Integer versionNo) {
        return new FileRecordRequest(actor, session, id(fileId), versionNo, requestId());
    }

    /** 分页结果。 */
    PagingResponseBody<FileRecordSummary> page(PagingResponseBody<FileRecordView> page) {
        return page.map(this::summary);
    }

    /** 列表行。 */
    FileRecordSummary summary(FileRecordView view) {
        return new FileRecordSummary(view.getFileId().toString(), FileAddress.of(view.getFileId()).value(),
            view.getDisplayName(), view.getSourceSystem(), view.getReasonCode(), view.getAccessLevel().getCode(),
            view.getCurrentVersionNo(), view.getPendingVersionNo(), view.getScanStatus().getCode(),
            view.getObjectStatus().getCode(), view.getMimeType(), view.getSizeBytes().toString(), view.getCreateTime(),
            view.getUpdateTime());
    }

    /** 详情：头部同列表行，附存储位置、公开地址、版本、检查结果与上传记录。 */
    FileRecordDetailResponseBody detail(FileRecordDetailView detail) {
        FileRecordSummary head = summary(detail.getFile());
        return new FileRecordDetailResponseBody(head.getFileId(), head.getFileAddress(), head.getDisplayName(),
            head.getSourceSystem(), head.getReasonCode(), head.getAccessLevel(), head.getCurrentVersionNo(),
            head.getPendingVersionNo(), head.getScanStatus(), head.getObjectStatus(), head.getMimeType(),
            head.getSizeBytes(), head.getCreateTime(), head.getUpdateTime(), detail.getContentHash(),
            decimal(detail.getUploaderId()), detail.getInstanceCode(), detail.getBucketName(), detail.getObjectRef(),
            detail.getPublicUrl(), detail.getVersionedUrl(), detail.getVersions().stream().map(this::version).toList(),
            detail.getScanResults().stream().map(this::scan).toList(),
            detail.getUploads().stream().map(this::upload).toList());
    }

    /** 下载授权；运营下载总是附件。 */
    FileDownloadResponseBody download(AuthorizeFileDownloadResponse response) {
        return new FileDownloadResponseBody(response.getUrl(), response.getExpiresAt(),
            response.getDisposition().getCode());
    }

    /** 一个版本。 */
    FileVersionRecord version(FileRecordDetailView.Version version) {
        return new FileVersionRecord(version.getVersionNo(), version.getVersionStatus().getCode(),
            version.getScanStatus().getCode(), version.getDisplayName(), version.getMimeType(),
            version.getSizeBytes().toString(), version.getContentHash(), decimal(version.getUploadId()),
            decimal(version.getUploaderId()), version.downloadable(), version.getAvailableTime(),
            version.getCreateTime());
    }

    /** 一条检查结果。 */
    FileScanRecord scan(FileRecordDetailView.ScanResult result) {
        return new FileScanRecord(result.getVersionNo(), result.getScanPolicyCode(), result.getRevisionNo(),
            result.getScannerCode(), result.getScanType(), result.getResultCode(), result.getAttemptNo(),
            result.getScannedTime());
    }

    /** 一条上传记录：有被替换文件的为 REPLACEMENT。 */
    FileUploadRecord upload(FileRecordDetailView.Upload upload) {
        return new FileUploadRecord(upload.getUploadId().toString(),
            upload.getReplacesFileId() == null ? "ORIGINAL" : "REPLACEMENT", upload.getReplacementNo(),
            upload.getStatus().getCode(), upload.getDisplayName(), upload.getExpectedMime(),
            upload.getExpectedSize().toString(), decimal(upload.getUploaderId()), upload.getResultVersionNo(),
            upload.getExpiresTime(), upload.getCreateTime(), upload.getUpdateTime());
    }

    /** 解析规范正整数 ID。 */
    long id(String value) {
        if (value == null || !value.matches(CANONICAL_ID)) {
            throw new InvalidParameterException("文件 ID 必须为规范正整数");
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException overflow) {
            throw new InvalidParameterException("文件 ID 必须为规范正整数");
        }
    }

    /** 可选 Code：为空不限制，不认识的值拒绝。 */
    static <T extends CodeEnum> T code(Class<T> type, String value,
        String message) {
        if (value == null || value.isEmpty()) {
            return null;
        }
        T parsed = EnumUtils.getEnumByCode(type, value);
        if (parsed == null) {
            throw new InvalidParameterException(message);
        }
        return parsed;
    }

    /** 当前请求 ID。 */
    static String requestId() {
        String requestId = MDC.get(ApiProblemProfile.REQUEST_ID_MDC_KEY);
        return requestId == null ? UNKNOWN_REQUEST : requestId;
    }

    /** 可空数字输出十进制字符串。 */
    static String decimal(Long value) {
        return value == null ? null : value.toString();
    }

    /** 空白文本视为未填写。 */
    static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}

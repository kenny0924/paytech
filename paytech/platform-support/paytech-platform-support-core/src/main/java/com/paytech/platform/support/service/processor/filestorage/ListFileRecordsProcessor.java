package com.paytech.platform.support.service.processor.filestorage;

import java.time.Duration;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.paytech.foundation.common.error.InvalidParameterException;
import com.paytech.foundation.processor.AbstractProcessor;
import com.paytech.foundation.web.paging.PagingRequestBody;
import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.platform.support.filedocument.domain.file.FileRecordQuery;
import com.paytech.platform.support.filedocument.domain.file.FileRecordView;
import com.paytech.platform.support.filedocument.domainservice.service.FileRecordService;
import com.paytech.platform.support.service.processor.filestorage.request.ListFileRecordsRequest;

import lombok.AllArgsConstructor;

/**
 * 文件记录分页查询：重检 FILE_RECORD_READ 后按条件数据库分页
 * <li>创建时间范围左闭右开且必填，跨度不超过 31 天；其他条件精确匹配。</li>
 * <li>权限与分页、计数在同一只读快照，页内记录与总数一致。</li>
 */
@Component
@AllArgsConstructor
@ConditionalOnProperty(name = "operations.authentication.enabled", havingValue = "true")
public class ListFileRecordsProcessor
    extends AbstractProcessor<ListFileRecordsRequest, PagingResponseBody<FileRecordView>> {

    /** 创建时间范围的最大跨度。 */
    static final Duration MAX_RANGE = Duration.ofDays(31);

    private final FileRecordService records;
    private final FileStorageManagementSupport support;

    /** 时间范围只依赖输入。 */
    @Override
    protected void validate(ListFileRecordsRequest request) {
        if (!request.getCreatedFrom().isBefore(request.getCreatedTo())) {
            throw new InvalidParameterException("createdFrom 必须早于 createdTo");
        }
        if (Duration.between(request.getCreatedFrom(), request.getCreatedTo()).compareTo(MAX_RANGE) > 0) {
            throw new InvalidParameterException("创建时间范围不能超过 31 天");
        }
    }

    @Override
    protected PagingResponseBody<FileRecordView> processRequest(ListFileRecordsRequest request) {
        PagingRequestBody paging = PagingRequestBody.of(request.getPage(), request.getSize());
        FileRecordQuery query = new FileRecordQuery(request.getCreatedFrom(), request.getCreatedTo(),
            request.getFileId(), request.getSourceSystem(), request.getReasonCode(), request.getAccessLevel(),
            request.getScanStatus(), request.getHasPendingReplacement());
        return support.read(request.getActor(), request.getSession(), () -> records.list(query, paging),
            FileStorageManagementSupport.RECORD_READ_PERMISSION);
    }
}

package com.paytech.platform.support.service.processor.files;

import java.time.Duration;
import java.util.Set;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.paytech.foundation.common.error.InvalidParameterException;
import com.paytech.foundation.processor.AbstractProcessor;
import com.paytech.foundation.web.paging.PagingRequestBody;
import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.platform.support.filedocument.domain.file.FileRecordQuery;
import com.paytech.platform.support.filedocument.domain.file.StoredFileView;
import com.paytech.platform.support.filedocument.domainservice.service.FileRecordService;
import com.paytech.platform.support.service.processor.files.request.ListCallerFilesRequest;

import lombok.AllArgsConstructor;

/**
 * 分页查询调用方自己来源的文件：来源条件固定为调用身份，其余条件按输入精确匹配后数据库分页
 * <li>登记时间范围左闭右开且必填，跨度不超过 31 天；每页 10、20 或 50 条。</li>
 * <li>只读、不加事务：页内记录与总数分两条 SQL 读取，允许观察相邻的已提交状态（与运营文件记录列表相同）。</li>
 */
@Component
@AllArgsConstructor
@ConditionalOnProperty(name = "files.enabled", havingValue = "true")
public class ListCallerFilesProcessor extends AbstractProcessor<ListCallerFilesRequest, PagingResponseBody<StoredFileView>> {

    /** 登记时间范围的最大跨度。 */
    static final Duration MAX_RANGE = Duration.ofDays(31);

    /** 允许的每页条数。 */
    static final Set<Integer> PAGE_SIZES = Set.of(10, 20, 50);

    private final FileRecordService records;

    /** 时间范围与每页条数只依赖输入。 */
    @Override
    protected void validate(ListCallerFilesRequest request) {
        if (!request.getCreatedFrom().isBefore(request.getCreatedTo())) {
            throw new InvalidParameterException("createdFrom 必须早于 createdTo");
        }
        if (Duration.between(request.getCreatedFrom(), request.getCreatedTo()).compareTo(MAX_RANGE) > 0) {
            throw new InvalidParameterException("登记时间范围不能超过 31 天");
        }
        if (request.getSize() != null && !PAGE_SIZES.contains(request.getSize())) {
            throw new InvalidParameterException("size 只能为 10、20 或 50");
        }
    }

    @Override
    protected PagingResponseBody<StoredFileView> processRequest(ListCallerFilesRequest request) {
        PagingRequestBody paging = PagingRequestBody.of(request.getPage(), request.getSize());
        FileRecordQuery query = new FileRecordQuery(request.getCreatedFrom(), request.getCreatedTo(), null,
            request.getSourceSystem(), request.getReasonCode(), request.getAccessLevel(), null, null,
            request.getUploaderAccountId());
        return records.listFiles(query, paging);
    }
}

package com.paytech.operations.console.bff.service.processor;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.paytech.foundation.common.error.InvalidParameterException;
import com.paytech.foundation.processor.AbstractProcessor;
import com.paytech.foundation.web.paging.PagingRequestBody;
import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.operations.console.bff.integration.PlatformPublicFileClient;
import com.paytech.operations.console.bff.service.model.fileupload.response.FileUploadRecordResponseBody;
import com.paytech.operations.console.bff.service.processor.request.ListFileUploadRecordsRequest;

import lombok.AllArgsConstructor;

/**
 * 文件上传页「最近上传」：本入口全部人员的公开上传，按登记时间倒序分页
 * <li>Platform 把来源限定为本 BFF，并固定原因 OPERATIONS_PUBLIC_ASSET 与访问级别 PUBLIC；mine=true 时上传人取当前会话账号。</li>
 * <li>时间范围缺省为截至请求时间的近 7 天；范围合法性（左闭右开、≤ 31 天）由 Platform 校验并返回 400。</li>
 */
@Component
@AllArgsConstructor
public class ListFileUploadRecordsProcessor
    extends AbstractProcessor<ListFileUploadRecordsRequest, PagingResponseBody<FileUploadRecordResponseBody>> {

    /** 缺省时间范围。 */
    static final Duration DEFAULT_RANGE = Duration.ofDays(7);

    /** 允许的每页条数，与 Platform 调用方文件列表一致。 */
    static final Set<Integer> PAGE_SIZES = Set.of(10, 20, 50);

    private final PlatformPublicFileClient files;

    /** 时间范围两端同时缺省或同时提供；每页条数只依赖输入。 */
    @Override
    protected void validate(ListFileUploadRecordsRequest request) {
        if ((request.getCreatedFrom() == null) != (request.getCreatedTo() == null)) {
            throw new InvalidParameterException("createdFrom 与 createdTo 必须同时提供");
        }
        if (request.getSize() != null && !PAGE_SIZES.contains(request.getSize())) {
            throw new InvalidParameterException("size 只能为 10、20 或 50");
        }
    }

    @Override
    protected PagingResponseBody<FileUploadRecordResponseBody> processRequest(ListFileUploadRecordsRequest request) {
        PagingRequestBody paging = PagingRequestBody.of(request.getPage(), request.getSize());
        Instant createdTo = request.getCreatedTo() == null ? request.getRequestTime() : request.getCreatedTo();
        Instant createdFrom = request.getCreatedFrom() == null ? createdTo.minus(DEFAULT_RANGE) : request.getCreatedFrom();
        String accountId = request.getOperator().accountId();
        String uploader = Boolean.TRUE.equals(request.getMine()) ? accountId : null;
        return files.files(createdFrom, createdTo, uploader, paging.page(), paging.size())
            .map(file -> FileUploadRecords.record(file, accountId));
    }
}

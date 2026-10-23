package com.paytech.platform.support.service.processor.filestorage;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.paytech.foundation.processor.AbstractProcessor;
import com.paytech.platform.support.filedocument.domain.file.FileRecordDetailView;
import com.paytech.platform.support.filedocument.domainservice.service.FileRecordService;
import com.paytech.platform.support.service.processor.filestorage.request.FileRecordRequest;

import lombok.AllArgsConstructor;

/** 文件记录详情：重检 FILE_RECORD_READ 后读取文件、存储位置、版本、检查结果与上传记录；不存在返回 404。 */
@Component
@AllArgsConstructor
@ConditionalOnProperty(name = "operations.authentication.enabled", havingValue = "true")
public class GetFileRecordProcessor extends AbstractProcessor<FileRecordRequest, FileRecordDetailView> {

    private final FileRecordService records;
    private final FileStorageManagementSupport support;

    @Override
    protected FileRecordDetailView processRequest(FileRecordRequest request) {
        return support.read(request.getActor(), request.getSession(), () -> records.detail(request.getFileId()),
            FileStorageManagementSupport.RECORD_READ_PERMISSION);
    }
}

package com.paytech.platform.support.filedocument.domainservice.service.impl;

import org.springframework.stereotype.Service;

import com.paytech.foundation.common.error.FailException;
import com.paytech.foundation.web.paging.PagingRequestBody;
import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.platform.support.api.enums.FileErrorCode;
import com.paytech.platform.support.filedocument.core.dal.dataobject.FileDescription;
import com.paytech.platform.support.filedocument.domain.file.FileRecordDetailView;
import com.paytech.platform.support.filedocument.domain.file.FileRecordQuery;
import com.paytech.platform.support.filedocument.domain.file.FileRecordView;
import com.paytech.platform.support.filedocument.domain.file.PublicFileUrl;
import com.paytech.platform.support.filedocument.domain.file.StoredFileView;
import com.paytech.platform.support.filedocument.domainservice.repository.FileLifecycleRepository;
import com.paytech.platform.support.filedocument.domainservice.repository.FileRecordRepository;
import com.paytech.platform.support.filedocument.domainservice.service.FileRecordService;

import lombok.AllArgsConstructor;

/**
 * 文件记录的只读组合查询
 * <li>详情的多次读取不开显式事务：各部分观察相邻已提交状态即可，页面以文件的当前版本号与版本历史为准。</li>
 */
@Service
@AllArgsConstructor
public class FileRecordServiceImpl implements FileRecordService {

    private final FileRecordRepository records;
    private final FileLifecycleRepository files;

    /** 数据库分页与相同条件的总数。 */
    @Override
    public PagingResponseBody<FileRecordView> list(FileRecordQuery query, PagingRequestBody paging) {
        return new PagingResponseBody<>(records.findPage(query, paging.size(), paging.offset()), paging.page(),
            paging.size(), records.count(query));
    }

    /** 数据库分页与相同条件的总数；每行按与按 ID 查询相同的规则计算公开地址。 */
    @Override
    public PagingResponseBody<StoredFileView> listFiles(FileRecordQuery query, PagingRequestBody paging) {
        return new PagingResponseBody<>(records.findDescriptionPage(query, paging.size(), paging.offset()).stream()
            .map(StoredFileView::of).toList(), paging.page(), paging.size(), records.count(query));
    }

    /** 文件元数据、存储位置与公开地址取组合读取结果，其余部分按文件 ID 读取。 */
    @Override
    public FileRecordDetailView detail(long fileId) {
        FileDescription row = files.describeFile(fileId);
        if (row == null) {
            throw new FailException(FileErrorCode.FILE_NOT_FOUND);
        }
        String publicUrl = PublicFileUrl.of(row.getAccessLevel(), row.getScanStatus(), row.getPublicBaseUrl(),
            row.getObjectRef());
        return new FileRecordDetailView(summary(row), row.getContentHash(), row.getUploaderId(), row.getProvider(),
            row.getContainer(), row.getObjectRef(), publicUrl,
            PublicFileUrl.versioned(publicUrl, row.getCurrentVersionNo()), records.findVersions(fileId),
            records.findScanResults(fileId), records.findUploads(fileId));
    }

    /** 详情头部与列表行同一组字段。 */
    static FileRecordView summary(FileDescription row) {
        FileRecordView view = new FileRecordView();
        view.setFileId(row.getFileId());
        view.setDisplayName(row.getDisplayName());
        view.setSourceSystem(row.getSourceSystem());
        view.setReasonCode(row.getReasonCode());
        view.setAccessLevel(row.getAccessLevel());
        view.setCurrentVersionNo(row.getCurrentVersionNo());
        view.setPendingVersionNo(row.getPendingVersionNo());
        view.setScanStatus(row.getScanStatus());
        view.setObjectStatus(row.getObjectStatus());
        view.setMimeType(row.getMimeType());
        view.setSizeBytes(row.getSizeBytes());
        view.setCreateTime(row.getCreateTime());
        view.setUpdateTime(row.getUpdateTime());
        return view;
    }
}

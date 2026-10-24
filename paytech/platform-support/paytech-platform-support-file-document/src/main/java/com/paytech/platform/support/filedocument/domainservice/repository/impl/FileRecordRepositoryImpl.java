package com.paytech.platform.support.filedocument.domainservice.repository.impl;

import java.util.List;

import org.springframework.stereotype.Repository;

import com.paytech.platform.support.filedocument.core.dal.dataobject.FileDescription;
import com.paytech.platform.support.filedocument.core.dal.mapper.FileRecordMapper;
import com.paytech.platform.support.filedocument.domain.file.FileRecordDetailView;
import com.paytech.platform.support.filedocument.domain.file.FileRecordQuery;
import com.paytech.platform.support.filedocument.domain.file.FileRecordView;
import com.paytech.platform.support.filedocument.domainservice.repository.FileRecordRepository;

import lombok.AllArgsConstructor;

/** 委托 Mapper 的文件记录查询。 */
@Repository
@AllArgsConstructor
public class FileRecordRepositoryImpl implements FileRecordRepository {

    private final FileRecordMapper mapper;

    /** 分页查询。 */
    @Override
    public List<FileRecordView> findPage(FileRecordQuery query, int limit, int offset) {
        return mapper.selectPage(query, limit, offset);
    }

    /** 文件元数据分页。 */
    @Override
    public List<FileDescription> findDescriptionPage(FileRecordQuery query, int limit, int offset) {
        return mapper.selectDescriptionPage(query, limit, offset);
    }

    /** 总数。 */
    @Override
    public long count(FileRecordQuery query) {
        return mapper.count(query);
    }

    /** 版本历史。 */
    @Override
    public List<FileRecordDetailView.Version> findVersions(long fileId) {
        return mapper.selectVersions(fileId);
    }

    /** 检查结果。 */
    @Override
    public List<FileRecordDetailView.ScanResult> findScanResults(long fileId) {
        return mapper.selectScanResults(fileId);
    }

    /** 上传记录。 */
    @Override
    public List<FileRecordDetailView.Upload> findUploads(long fileId) {
        return mapper.selectUploads(fileId);
    }
}

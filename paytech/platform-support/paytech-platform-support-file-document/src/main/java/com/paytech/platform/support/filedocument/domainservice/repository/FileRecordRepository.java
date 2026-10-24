package com.paytech.platform.support.filedocument.domainservice.repository;

import java.util.List;

import com.paytech.platform.support.filedocument.core.dal.dataobject.FileDescription;
import com.paytech.platform.support.filedocument.domain.file.FileRecordDetailView;
import com.paytech.platform.support.filedocument.domain.file.FileRecordQuery;
import com.paytech.platform.support.filedocument.domain.file.FileRecordView;

/** 文件记录的只读组合查询；只访问 file-document 自有表。 */
public interface FileRecordRepository {

    /** 按条件分页，按创建时间倒序、文件 ID 倒序。 */
    List<FileRecordView> findPage(FileRecordQuery query, int limit, int offset);

    /** 按条件分页的文件元数据，按创建时间倒序、文件 ID 倒序。 */
    List<FileDescription> findDescriptionPage(FileRecordQuery query, int limit, int offset);

    /** 与分页相同条件的总数。 */
    long count(FileRecordQuery query);

    /** 文件的全部版本，按版本号倒序。 */
    List<FileRecordDetailView.Version> findVersions(long fileId);

    /** 文件的全部检查结果。 */
    List<FileRecordDetailView.ScanResult> findScanResults(long fileId);

    /** 文件的全部上传记录。 */
    List<FileRecordDetailView.Upload> findUploads(long fileId);
}

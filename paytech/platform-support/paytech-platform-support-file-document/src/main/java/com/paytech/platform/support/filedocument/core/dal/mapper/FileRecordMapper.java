package com.paytech.platform.support.filedocument.core.dal.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Param;

import com.paytech.platform.support.filedocument.core.dal.dataobject.FileDescription;
import com.paytech.platform.support.filedocument.domain.file.FileRecordDetailView;
import com.paytech.platform.support.filedocument.domain.file.FileRecordQuery;
import com.paytech.platform.support.filedocument.domain.file.FileRecordView;

/** 文件记录的只读组合查询（文件、版本、上传、检查结果与策略）；SQL 位于 FileRecordMapper.xml。 */
public interface FileRecordMapper {

    /** 按条件分页，按创建时间倒序、文件 ID 倒序。 */
    List<FileRecordView> selectPage(@Param("query") FileRecordQuery query, @Param("limit") int limit,
        @Param("offset") int offset);

    /** 按条件分页的文件元数据（与按 ID 查询的组合读取同一组列），按创建时间倒序、文件 ID 倒序。 */
    List<FileDescription> selectDescriptionPage(@Param("query") FileRecordQuery query, @Param("limit") int limit,
        @Param("offset") int offset);

    /** 与分页相同条件的总数。 */
    long count(@Param("query") FileRecordQuery query);

    /** 文件的全部版本与产生它们的上传，按版本号倒序。 */
    List<FileRecordDetailView.Version> selectVersions(@Param("fileId") long fileId);

    /** 文件的全部检查结果，按版本号倒序、检查时间倒序。 */
    List<FileRecordDetailView.ScanResult> selectScanResults(@Param("fileId") long fileId);

    /** 文件的首次上传与全部替换上传，按创建时间倒序。 */
    List<FileRecordDetailView.Upload> selectUploads(@Param("fileId") long fileId);
}

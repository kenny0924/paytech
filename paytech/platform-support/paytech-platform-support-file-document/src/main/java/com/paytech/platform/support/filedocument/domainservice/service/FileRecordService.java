package com.paytech.platform.support.filedocument.domainservice.service;

import com.paytech.foundation.web.paging.PagingRequestBody;
import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.platform.support.filedocument.domain.file.FileRecordDetailView;
import com.paytech.platform.support.filedocument.domain.file.FileRecordQuery;
import com.paytech.platform.support.filedocument.domain.file.FileRecordView;

/** 运营文件记录查询：只读，不做文件级权限（调用方已重检运营权限）。 */
public interface FileRecordService {

    /**
     * 按条件分页查询文件记录；数据库 LIMIT/OFFSET 与 COUNT 使用相同条件
     *
     * @param query 已校验的筛选条件
     * @param paging 已校验的分页参数
     * @return 当前页与总数
     */
    PagingResponseBody<FileRecordView> list(FileRecordQuery query, PagingRequestBody paging);

    /**
     * 文件记录详情；不存在返回 FILE_NOT_FOUND
     *
     * @param fileId 文件 ID
     * @return 文件、存储位置、公开地址、版本、检查结果与上传记录
     */
    FileRecordDetailView detail(long fileId);
}

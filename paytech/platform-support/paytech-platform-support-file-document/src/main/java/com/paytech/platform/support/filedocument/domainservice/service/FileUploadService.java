package com.paytech.platform.support.filedocument.domainservice.service;

import java.time.Instant;

import com.paytech.platform.support.filedocument.domain.file.UploadModels.Start;
import com.paytech.platform.support.filedocument.domain.file.UploadModels.Upload;
import com.paytech.platform.support.filedocument.domain.storage.StorageWriteTarget;

/** 向组合根发布上传规则，跨模块不暴露 DataObject。 */
public interface FileUploadService {

    /**
     * 按路由目标与当前有效策略登记一次上传；单条插入，需要与其他写入原子提交时由调用方开启事务
     *
     * @param id 预先生成的上传 ID
     * @param start 来源、原因、上传人与声明的文件元数据
     * @param target 路由选择的实例、桶、扫描策略与上传期限
     * @param now 本次登记时间（UTC 微秒）
     * @return 固定后的上传目标
     */
    Upload create(long id, Start start, StorageWriteTarget target, Instant now);
}

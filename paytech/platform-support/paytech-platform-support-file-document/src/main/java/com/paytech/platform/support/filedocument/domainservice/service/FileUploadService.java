package com.paytech.platform.support.filedocument.domainservice.service;

import java.time.Instant;

import com.paytech.platform.support.filedocument.domain.file.UploadModels.Constraints;
import com.paytech.platform.support.filedocument.domain.file.UploadModels.ReplacementStart;
import com.paytech.platform.support.filedocument.domain.file.UploadModels.Start;
import com.paytech.platform.support.filedocument.domain.file.UploadModels.Upload;
import com.paytech.platform.support.filedocument.domain.storage.StorageWriteTarget;

/** 向组合根发布上传规则，跨模块不暴露 DataObject。 */
public interface FileUploadService {

    /**
     * 按路由目标与当前有效策略登记一次上传；单条插入，需要与其他写入原子提交时由调用方开启事务
     *
     * @param id 预先生成的上传 ID
     * @param start 来源、原因、访问级别、上传人与声明的文件元数据
     * @param target 路由选择的实例、桶、扫描策略与上传期限
     * @param now 本次登记时间（UTC 微秒）
     * @return 固定后的上传目标
     */
    Upload create(long id, Start start, StorageWriteTarget target, Instant now);

    /**
     * 按原文件策略代码当前的 ACTIVE 修订登记一次替换上传：对象名、位置、来源、原因与访问级别沿用原文件
     * <li>参与调用方事务；替换序号由唯一键 (实例, 桶, 对象名, 替换序号) 兜底。</li>
     *
     * @param id 预先生成的上传 ID
     * @param start 可替换的原文件、存储基准版本与新内容的声明
     * @param now 本次登记时间（UTC 微秒）
     * @return 固定后的替换上传
     */
    Upload createReplacement(long id, ReplacementStart start, Instant now);

    /**
     * 扫描策略代码在指定时间唯一有效修订的上传限制；只读
     * <li>与开始上传、开始替换核对类型与大小读取同一修订、按同一规则解析：没有唯一有效修订、修订结构不支持、没有扫描要求或允许类型
     * 损坏时返回不可用（503）。</li>
     *
     * @param policyCode 路由冻结的扫描策略代码
     * @param now 查询时间（UTC 微秒）
     * @return 修订号、允许类型与大小上限
     */
    Constraints constraints(String policyCode, Instant now);
}

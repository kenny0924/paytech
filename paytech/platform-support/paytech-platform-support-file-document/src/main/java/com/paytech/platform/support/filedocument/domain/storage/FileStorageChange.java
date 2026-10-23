package com.paytech.platform.support.filedocument.domain.storage;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 一次存储管理写入的结果与审计差异；由 Service 在条件更新成功后构造。 */
@Getter
@AllArgsConstructor
public class FileStorageChange {

    /** 存储实例对象类型。 */
    public static final String INSTANCE = "FILE_STORAGE_INSTANCE";

    /** 存储桶对象类型。 */
    public static final String BUCKET = "FILE_STORAGE_BUCKET";

    /** 写入路由对象类型。 */
    public static final String ROUTE = "FILE_STORAGE_ROUTE";

    /** 检查策略修订对象类型。 */
    public static final String SCAN_POLICY = "FILE_SCAN_POLICY";

    /** 对象类型：INSTANCE、BUCKET、ROUTE 或 SCAN_POLICY。 */
    private final String objectType;

    /** 对象主键。 */
    private final long objectId;

    /**
     * 对象编码
     * <li>实例为实例代码，桶为 {实例代码}/{桶名}，路由为 {来源系统}/{访问级别}，检查策略为 {策略代码}/{修订号}。</li>
     */
    private final String objectCode;

    /** 保存后的状态 Code；路由没有状态，删除后为空。 */
    private final String status;

    /** 修改前版本；新建为 0。 */
    private final int beforeVersion;

    /** 修改后版本；删除时为删除前版本。 */
    private final int afterVersion;

    /** 字段差异。 */
    private final List<FileStorageFieldChange> changes;
}

package com.paytech.platform.support.filedocument.core.dal.dataobject;

import java.time.Instant;

import com.paytech.platform.support.filedocument.core.enums.FileAccessLevel;

import lombok.Getter;
import lombok.Setter;

/** t_file_storage_route 映射对象；只在 file-document 模块内使用，快照中的对象只读。 */
@Getter
@Setter
public class FileStorageRoute {

    /** 写入路由 Snowflake 主键。 */
    private Long routeId;

    /**
     * 来源系统
     * <li>内部调用身份的服务名，或 * 表示默认路由。</li>
     */
    private String sourceSystem;

    /** 访问级别；与来源系统共同构成路由键。 */
    private FileAccessLevel accessLevel;

    /** 新上传写入的存储桶 ID。 */
    private Long bucketId;

    /** 新上传冻结的扫描策略代码。 */
    private String scanPolicyCode;

    /**
     * 上传授权有效秒数
     * <li>60—900。</li>
     */
    private Integer maxUploadSeconds;

    /** 乐观并发版本。 */
    private Integer version;

    /** 创建时间（UTC）。 */
    private Instant createTime;

    /** 最后更新时间（UTC）。 */
    private Instant updateTime;
}

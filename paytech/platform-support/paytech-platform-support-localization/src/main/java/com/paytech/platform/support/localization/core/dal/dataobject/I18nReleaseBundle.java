package com.paytech.platform.support.localization.core.dal.dataobject;

import java.time.Instant;

import lombok.Getter;
import lombok.Setter;

/** t_i18n_release_bundle 映射对象；只在 localization 模块内使用。 */
@Getter
@Setter
public class I18nReleaseBundle {

    /** 服务侧生成的语言包 Snowflake 主键。 */
    private Long bundleId;

    /** 所属发布，取值为 t_i18n_release.release_id。 */
    private Long releaseId;

    /** 语言包语言，取值为 t_i18n_locale.locale_code。 */
    private String localeCode;

    /** 语言包命名空间编码，发布时的 t_i18n_namespace.namespace_code。 */
    private String namespaceCode;

    /** 文件服务地址 /files/v1/{fileId}；文件位于公开桶，公开地址由文件服务在读取时计算，不落库。 */
    private String fileAddress;

    /** 语言包内容 SHA-256，小写十六进制。 */
    private String contentHash;

    /** 语言包字节数。 */
    private Integer byteSize;

    /** 该记录在 Platform Support 数据库中的 UTC 微秒创建时间；创建后不可变，没有更新时间。 */
    private Instant createTime;
}

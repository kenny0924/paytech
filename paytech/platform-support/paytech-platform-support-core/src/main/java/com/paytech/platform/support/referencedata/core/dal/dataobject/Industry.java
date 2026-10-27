package com.paytech.platform.support.referencedata.core.dal.dataobject;

import java.time.Instant;

import com.paytech.platform.support.api.enums.ReferenceDataStatus;

import lombok.Getter;
import lombok.Setter;

/** t_ref_industry 映射对象；只在 referencedata 模块内使用。 */
@Getter
@Setter
public class Industry {

    /**
     * 行业主键
     * <li>初始化数据使用 4001 起的固定 ID。</li>
     */
    private Long id;

    /** 平台行业编码。 */
    private String industryCode;

    /**
     * 上级大类编码
     * <li>大类为空。</li>
     */
    private String parentIndustryCode;

    /**
     * 层级
     * <li>1 为大类，2 为细类；列名 industry_level。</li>
     */
    private Integer level;

    /** 英文名称。 */
    private String name;

    /**
     * 英文选择提示
     * <li>没有时为空。</li>
     */
    private String description;

    /**
     * 建议 MCC
     * <li>只有细类有，大类为空。</li>
     */
    private String defaultMccCode;

    /**
     * 行业图标文件地址
     * <li>文件服务地址 /files/v1/{fileId}；没有时为空。</li>
     */
    private String logoFileAddress;

    /** 同一层级内的显示顺序，数值越小越靠前。 */
    private Integer sortNo;

    /** 状态。 */
    private ReferenceDataStatus status;

    /** 乐观并发版本，从 1 开始。 */
    private Integer version;

    /** 创建时间（UTC）。 */
    private Instant createTime;

    /** 最后更新时间（UTC）。 */
    private Instant updateTime;
}

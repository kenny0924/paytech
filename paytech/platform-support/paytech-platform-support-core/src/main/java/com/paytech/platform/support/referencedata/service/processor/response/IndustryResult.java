package com.paytech.platform.support.referencedata.service.processor.response;

import com.paytech.platform.support.api.enums.ReferenceDataStatus;
import com.paytech.platform.support.referencedata.core.dal.dataobject.Industry;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 跨模块发布的行业结果；不暴露 DataObject。 */
@Getter
@AllArgsConstructor
public class IndustryResult {

    /** 行业编码。 */
    private final String industryCode;

    /** 上级大类编码；大类为空。 */
    private final String parentIndustryCode;

    /** 层级：1 为大类，2 为细类。 */
    private final int level;

    /** 英文名称。 */
    private final String name;

    /** 英文说明；没有为空。 */
    private final String description;

    /** 建议 MCC；大类为空。 */
    private final String defaultMccCode;

    /** 图标文件地址；没有为空。 */
    private final String logoFileAddress;

    /** 图标公开地址；没有图标或文件不可公开访问为空。 */
    private final String logoPublicUrl;

    /** 同级排序号。 */
    private final int sortNo;

    /** 状态。 */
    private final ReferenceDataStatus status;

    /** 乐观并发版本。 */
    private final int version;

    /** 由本模块 DataObject 与已换取的公开地址构造。 */
    public static IndustryResult of(Industry row, String logoPublicUrl) {
        return new IndustryResult(row.getIndustryCode(), row.getParentIndustryCode(), row.getLevel(), row.getName(),
            row.getDescription(), row.getDefaultMccCode(), row.getLogoFileAddress(), logoPublicUrl, row.getSortNo(),
            row.getStatus(), row.getVersion());
    }
}

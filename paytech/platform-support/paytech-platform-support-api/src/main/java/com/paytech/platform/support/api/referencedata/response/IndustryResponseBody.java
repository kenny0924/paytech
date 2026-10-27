package com.paytech.platform.support.api.referencedata.response;

import lombok.Getter;
import lombok.Setter;

/** 参考数据目录中的一个行业大类或细类；字段与 t_ref_industry 的业务列一致。 */
@Getter
@Setter
public class IndustryResponseBody {

    /**
     * 行业编码
     * <li>平台自有编码，最长 64 字符。</li>
     */
    private String industryCode;

    /**
     * 上级大类编码
     * <li>大类为空；细类为所属大类编码。</li>
     */
    private String parentIndustryCode;

    /**
     * 层级
     * <li>1 为大类，2 为细类。</li>
     */
    private Integer level;

    /**
     * 英文名称
     */
    private String name;

    /**
     * 英文说明
     * <li>最长 512 字符；没有时为空。</li>
     */
    private String description;

    /**
     * 默认 MCC 编码
     * <li>四位数字，保留前导零；只有细类可能有，没有时为空。</li>
     */
    private String defaultMccCode;

    /**
     * logo 文件地址
     * <li>文件服务地址 {@code /files/v1/{fileId}}；没有 logo 时为空。</li>
     */
    private String logoFileAddress;

    /**
     * logo 公开访问地址
     * <li>由 Platform 按文件地址批量换取；没有 logo、文件不是公开文件或检查未通过时为空，调用方显示占位。</li>
     */
    private String logoPublicUrl;

    /**
     * 同级排序号
     * <li>同一上级内升序排列。</li>
     */
    private Integer sortNo;

    /**
     * 状态
     * <li>{@code ReferenceDataStatus} Code：DRAFT、ACTIVE、INACTIVE；只有 ACTIVE 允许被新业务选择。</li>
     */
    private String status;

    /**
     * 乐观并发版本
     * <li>正整数，从 1 开始。</li>
     */
    private Integer version;
}

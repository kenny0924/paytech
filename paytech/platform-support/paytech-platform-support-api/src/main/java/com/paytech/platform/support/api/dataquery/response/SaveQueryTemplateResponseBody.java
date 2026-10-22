package com.paytech.platform.support.api.dataquery.response;

import java.util.List;

import lombok.Getter;
import lombok.Setter;

/** 模板保存结果：新建、修改与恢复共用。 */
@Getter
@Setter
public class SaveQueryTemplateResponseBody {

    /** 模板 ID，十进制字符串。 */
    private String templateId;

    /** 模板编码。 */
    private String templateCode;

    /** 模板状态：ACTIVE、INACTIVE。 */
    private String status;

    /** 模板乐观并发版本，下一次写操作按此传 expectedVersion。 */
    private int version;

    /** 本次保存生成的版本号，立即生效。 */
    private int currentVersionNo;

    /** 警告，不阻止保存；没有时为空列表。 */
    private List<QueryTemplateProblem> warnings;
}

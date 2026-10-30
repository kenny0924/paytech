package com.paytech.tax.api.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/** 单据开具状态（t_tax_document.issue_status）；号码一经占用不回收。 */
public enum TaxDocumentIssueStatus implements CodeEnum {
    /** 已分号、文件尚未生成或上传；补偿任务重试同一单据 */
    RESERVED,

    /** 文件已存入 file 服务，documentFileAddress 可下载 */
    ISSUED,

    /** 号码已占用但最终未发行（本期无入口） */
    VOID;

    /** 数据库、接口与消息使用的稳定编码，与常量名一致。 */
    @Override
    public String getCode() {
        return name();
    }
}

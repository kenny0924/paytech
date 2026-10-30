package com.paytech.client.master.domain.classification;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** Client 主经营分类查询结果：经营资料的确认状态与主分类（t_client_business_profile + t_client_business_classification）。 */
@Getter
@AllArgsConstructor
public class ClientBusinessClassificationView {
    /** Client ID。 */
    private final long clientId;

    /** 分类已确认。 */
    private final boolean confirmed;

    /** 主分类行业代码；未确认或没有主分类时为空。 */
    private final String industryCode;

    /** 主分类 MCC；未确认或没有主分类时为空。 */
    private final String mccCode;
}

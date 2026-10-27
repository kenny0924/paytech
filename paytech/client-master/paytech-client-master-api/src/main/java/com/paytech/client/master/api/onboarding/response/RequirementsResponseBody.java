package com.paytech.client.master.api.onboarding.response;

import java.util.List;
import lombok.Getter;
import lombok.Setter;

/** CM-7 申请需求清单：实时计算，与正式提交校验同源。 */
@Getter
@Setter
public class RequirementsResponseBody {
    /** 申请 ID。 */
    private String applicationId;

    /** 申请状态。 */
    private String status;

    /** 是否没有阻止提交的项（CURRENTLY_DUE / PAST_DUE）。 */
    private boolean ready;

    /** 退回补件截止 UTC 时间，可空。 */
    private String dueTime;

    /** 全部清单项，按主体与 Schema 顺序。 */
    private List<RequirementItemResponseBody> items;
}

package com.paytech.risk.decision.api.control.response;

import com.paytech.risk.decision.api.control.request.UnderwritingInputBody;

import lombok.Getter;
import lombok.Setter;

/** 承保版本保存的输入快照：与请求输入同字段，另带 CRR 缺失标记。 */
@Getter
@Setter
public class UnderwritingSnapshotBody extends UnderwritingInputBody {

    /** 尽调未评 CRR（按 MEDIUM 处理）时为 true。 */
    private Boolean crrMissing;
}

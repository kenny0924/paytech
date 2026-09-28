package com.paytech.risk.decision.domain.variable;

import com.paytech.foundation.common.error.ErrorException;
import com.paytech.risk.decision.core.dal.dataobject.*;
import com.paytech.risk.decision.domain.enums.*;
import com.paytech.risk.decision.domain.variable.RiskVariable.*;
import com.paytech.risk.decision.domainservice.convert.variable.RiskVariablePersistenceConverter;
import static com.paytech.risk.decision.domainservice.convert.variable.RiskVariablePersistenceConverter.*;
import static com.paytech.risk.decision.domainservice.policy.VariableManagementPolicy.*;
import static com.paytech.risk.decision.domainservice.service.RiskVariableException.Reason.*;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** 跨模块详情响应，读取时刻只用于当前值展示。 */
@Getter
@AllArgsConstructor
public class VariableDetailResponse {
    /** 当前详情投影。 */
    private final View value;

    /** UTC 微秒读取时刻。 */
    private final Instant valuesAsOf;

}

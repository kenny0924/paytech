package com.paytech.risk.decision.service.processor;

import java.util.Arrays;
import java.util.List;

import org.springframework.stereotype.Component;

import com.paytech.foundation.common.error.InvalidParameterException;
import com.paytech.foundation.processor.AbstractProcessor;
import com.paytech.risk.decision.domain.enums.DecisionValueType;
import com.paytech.risk.decision.domain.enums.NumericUnit;
import com.paytech.risk.decision.domain.enums.VariableFormat;
import com.paytech.risk.decision.domain.enums.VariableSource;
import com.paytech.risk.decision.domain.variable.DecisionSourceContracts;
import com.paytech.risk.decision.domain.variable.VariableAuthoringCapabilities;
import com.paytech.risk.decision.service.processor.request.VariableAuthoringContextRequest;

/**
 * 返回工程已接通的变量编排能力，无数据库或外部调用
 * <li>按决策点精确筛选来源合同，不以最新版本或固定变量名单替代变量库。</li>
 */
@Component
public class VariableAuthoringContextProcessor extends AbstractProcessor<VariableAuthoringContextRequest, VariableAuthoringCapabilities> {
    /** 集合成员数量上限。 */
    static final int MEMBER_LIMIT = 1000;

    /** 整批成员请求的 UTF-8 字节上限，与严格请求读取器一致。 */
    static final int MEMBER_BODY_BYTES = 65536;

    /** 规则内联集合元素上限。 */
    static final int LITERAL_SET_LIMIT = 100;

    /** 未支持的决策点只依赖输入，在处理前拒绝。 */
    @Override
    protected void validate(VariableAuthoringContextRequest request) {
        String point = request.getPointCode();
        if (point != null && !DecisionSourceContracts.supports(point)) {
            throw new InvalidParameterException("决策点尚未支持");
        }
    }

    @Override
    protected VariableAuthoringCapabilities processRequest(VariableAuthoringContextRequest request) {
        String point = request.getPointCode();
        var points = DecisionSourceContracts.all().stream().map(DecisionSourceContracts.Contract::pointCode).distinct().toList();
        var contracts = DecisionSourceContracts.all().stream().filter(c -> point == null || point.equals(c.pointCode())).toList();
        var creatable = Arrays.stream(VariableSource.values()).filter(source -> source != VariableSource.NODE_OUTPUT).toList();
        return new VariableAuthoringCapabilities(points, contracts, List.of(VariableSource.values()), creatable,
            List.of(DecisionValueType.values()), List.of(VariableFormat.values()), List.of(NumericUnit.values()),
            MEMBER_LIMIT, MEMBER_BODY_BYTES, LITERAL_SET_LIMIT);
    }
}

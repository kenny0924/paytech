package com.paytech.risk.decision.service.processor;

import com.paytech.risk.decision.domain.authoring.DecisionPointAuthoringDescriptor;
import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import com.paytech.foundation.common.error.ErrorException;
import com.paytech.foundation.common.error.InvalidParameterException;
import com.paytech.foundation.processor.AbstractProcessor;
import com.paytech.risk.decision.core.dal.dataobject.DecisionFlowRow;
import com.paytech.risk.decision.core.dal.dataobject.DecisionFlowVersionRow;
import com.paytech.risk.decision.core.dal.dataobject.DecisionPoint;
import com.paytech.risk.decision.domain.enums.DecisionPointStatus;
import com.paytech.risk.decision.domain.enums.VariableSource;
import com.paytech.risk.decision.domain.flow.DecisionFlowGraphAssembler;
import com.paytech.risk.decision.domain.flow.DecisionFlowValidator;
import com.paytech.risk.decision.domain.flow.DecisionFlowVariableValidator;
import com.paytech.risk.decision.domain.flow.DecisionFlow.*;
import com.paytech.risk.decision.domain.rule.DecisionRuleSetAggregate;
import com.paytech.risk.decision.domain.variable.RiskVariable;
import com.paytech.risk.decision.domain.variable.VariableFlowConfiguration;
import com.paytech.risk.decision.domain.variable.VariableRuleConfiguration;
import com.paytech.risk.decision.domainservice.repository.*;
import com.paytech.risk.decision.domainservice.service.*;
import com.paytech.risk.decision.domainservice.service.DecisionFlowConfigurationException.Reason;
import com.paytech.risk.decision.service.processor.request.ValidateFlowRequest;

import lombok.AllArgsConstructor;

/** 只读检查合法草稿，保留 valid=false 的问题报告，不预留版本或修改目录。 */
@Component
@AllArgsConstructor
public class ValidateFlowProcessor extends AbstractProcessor<ValidateFlowRequest, Validation> {
    private final RiskVariableService variables;
    private final DecisionFlowValidator validator;
    private final DecisionRuleSetService ruleService;
    private final DecisionRuleSetRepository rules;
    private final DecisionFlowEdgeRepository edges;
    private final DecisionFlowGraphAssembler assembler;
    private final DecisionFlowNodeRepository nodes;
    private final DecisionFlowRootRepository roots;
    private final PlatformTransactionManager transactionManager;
    private final DecisionFlowReferenceService references;
    private final DecisionFlowVariableValidator variableValidator;
    private final DecisionFlowVersionRepository versions;

    /** 结构问题直接报告；需要目录的检查在同一只读快照内完成。 */
    @Override
    protected Validation processRequest(ValidateFlowRequest request) {
        Save command = request.getConfiguration();
        Checked checked = validator.check(command);
        if (!checked.validation().valid()) {
            return checked.validation();
        }
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        transaction.setReadOnly(true);
        transaction.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        return transaction.execute(status -> validateReferences(command, checked));
    }

    /** 保留浏览器节点顺序定位问题，准确引用与变量读取均不获取写锁。 */
    private Validation validateReferences(Save command, Checked checked) {
        DecisionPoint point = point(command.pointCode());
        DecisionFlowRow root = roots.findByCode(point.decisionPointId(), command.flowCode()).orElse(null);
        if (command.baseFlowVersionId() != null && root == null) {
            return baseConflict("编辑的流程不存在，请重新载入后编辑");
        }
        DecisionFlowVersionRow head;
        try {
            head = verifyBase(command, root);
        } catch (DecisionFlowConfigurationException failure) {
            return baseConflict(failure.getMessage());
        }
        Graph base = baseGraph(command.pointCode(), root, head);
        Map<String, Node> normalizedNodes =
            checked.normalized().graph().nodes().stream().collect(Collectors.toMap(Node::nodeKey, Function.identity()));
        Graph ordered =
            new Graph(1, command.graph().nodes().stream().map(n -> normalizedNodes.get(n.nodeKey())).toList(),
                checked.normalized().graph().edges());
        List<Issue> issues = new ArrayList<>(references.validateForQuery(point.decisionPointId(), point.pointCode(), ordered, base));
        if (issues.isEmpty()) {
            issues.addAll(checkVariables(command.pointCode(), ordered).issues());
        }
        return new Validation(issues.isEmpty(), issues.stream().limit(100).toList(), issues.size() > 100);
    }

    /** 编辑基准问题与图问题同样作为校验结果报告，不转为资源错误。 */
    private static Validation baseConflict(String message) {
        return new Validation(false, List.of(new Issue("BASE_CONFLICT", null, null, "baseFlowVersionId", message)), false);
    }

    /** 当前可编辑决策点由本库目录确认。 */
    private DecisionPoint point(String code) {
        if (DecisionPointAuthoringDescriptor.find(code).isEmpty()) {
            throw new DecisionConfigurationNotFoundException();
        }
        DecisionPoint point = rules.findPointByCode(code).orElseThrow(DecisionConfigurationNotFoundException::new);
        if (point.status() != DecisionPointStatus.ACTIVE) {
            throw new InvalidParameterException("决策点当前不可配置");
        }
        return point;
    }

    /** 新建编码不可复用；编辑只允许从已存在流程的当前头版本继续。 */
    private DecisionFlowVersionRow verifyBase(Save command, DecisionFlowRow root) {
        if (command.baseFlowVersionId() == null) {
            if (root != null) {
                throw new DecisionFlowConfigurationException(Reason.CODE_EXISTS, "流程编码已存在，删除后也不能复用", List.of());
            }
            return null;
        }
        var head = versions.findById(root.currentFlowVersionId()).orElseThrow(() -> new ErrorException("流程当前版本缺失"));
        root.verifyHead(head);
        root.verifySaveBase(command.baseFlowVersionId(), command.expectedVersion());
        return head;
    }

    /** 仅从真实基准版本读取保存图，已退休选择的沿用资格不由客户端声明。 */
    private Graph baseGraph(String pointCode, DecisionFlowRow root, DecisionFlowVersionRow head) {
        return root == null ? null : assembler.assemble(pointCode, root, head,
            nodes.findByVersion(head.flowVersionId()), edges.findByVersion(head.flowVersionId()));
    }

    /** 读取准确规则版本，随后由调用方按用途读取或锁定变量目录。 */
    private Map<Long, DecisionRuleSetAggregate> referencedRules(Graph graph) {
        Map<Long, DecisionRuleSetAggregate> result = new HashMap<>();
        graph.nodes().stream().filter(n -> "RULE_SET".equals(n.type())).map(Node::ruleSetVersionId).distinct().sorted()
            .forEach(id -> result.put(id, ruleService.detailForPersistedReference(id)));
        return result;
    }

    /** 查询当前变量事实，保留目录错误及条件路径，不对合法草稿伪造成功。 */
    private DecisionFlowVariableValidator.Result checkVariables(String pointCode, Graph graph) {
        List<Issue> legacy = DecisionFlowVariableValidator.legacyIssues(graph);
        if (!legacy.isEmpty()) {
            return new DecisionFlowVariableValidator.Result(graph, legacy);
        }
        Map<Long, DecisionRuleSetAggregate> ruleSets = referencedRules(graph);
        Set<String> codes = new TreeSet<>(VariableFlowConfiguration.references(graph));
        ruleSets.values().forEach(rule -> codes.addAll(VariableRuleConfiguration.references(rule)));
        Map<String, RiskVariable.Entry> definitions = new HashMap<>();
        Map<String, List<RiskVariable.Member>> collections = new HashMap<>();
        try {
            var catalog = variables.currentValues(codes, Instant.EPOCH);
            if (!catalog.issues().isEmpty()) {
                return new DecisionFlowVariableValidator.Result(graph,
                    catalog.issues().stream().map(i -> new Issue(i.code(), null, null, "graph", i.message())).toList());
            }
            catalog.variables().forEach((code, value) -> definitions.put(code, value.entry()));
            definitions.values().stream().filter(e -> e.definition().sourceType() == VariableSource.COLLECTION)
                .forEach(e -> collections.put(e.definition().variableCode(), variables.collection(e)));
        } catch (RiskVariableException failure) {
            return new DecisionFlowVariableValidator.Result(graph,
                List.of(new Issue(failure.reason().getCode(), null, null, "graph", failure.getMessage())));
        }
        return variableValidator.check(pointCode, graph, ruleSets, definitions, collections);
    }
}

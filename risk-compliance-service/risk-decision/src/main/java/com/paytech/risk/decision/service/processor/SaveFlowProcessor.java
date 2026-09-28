package com.paytech.risk.decision.service.processor;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.paytech.foundation.common.error.ErrorException;
import com.paytech.foundation.common.error.InvalidParameterException;
import com.paytech.foundation.redis.core.autoconfigure.RedisResourceLockProperties;
import com.paytech.foundation.redis.core.processor.AbstractLockProcessor;
import com.paytech.risk.decision.core.dal.dataobject.*;
import com.paytech.risk.decision.domain.authoring.CheckoutPrepareAuthoringDescriptor;
import com.paytech.risk.decision.domain.enums.*;
import com.paytech.risk.decision.domain.flow.*;
import com.paytech.risk.decision.domain.flow.DecisionFlow.*;
import com.paytech.risk.decision.domain.rule.DecisionRuleSetAggregate;
import com.paytech.risk.decision.domain.variable.RiskVariable;
import com.paytech.risk.decision.domain.variable.VariableFlowConfiguration;
import com.paytech.risk.decision.domain.variable.VariableRuleConfiguration;
import com.paytech.risk.decision.domainservice.port.DecisionIdentifierGenerator;
import com.paytech.risk.decision.domainservice.repository.*;
import com.paytech.risk.decision.domainservice.service.*;
import com.paytech.risk.decision.domainservice.service.DecisionFlowConfigurationException.Reason;
import com.paytech.risk.decision.service.processor.request.SaveFlowRequest;

import lombok.AllArgsConstructor;

/** 完整流程保存的 Owner 编排，资源锁包住本地四表原子写入。 */
@Component
@AllArgsConstructor
public class SaveFlowProcessor extends AbstractLockProcessor<SaveFlowRequest, Saved> {
    private final Clock clock;
    private final RiskVariableService variables;
    private final DecisionFlowJsonCodec codec;
    private final DecisionFlowValidator validator;
    private final DecisionRuleSetService ruleService;
    private final DecisionRuleSetRepository rules;
    private final DecisionFlowEdgeRepository edges;
    private final DecisionFlowGraphAssembler assembler;
    private final DecisionFlowNodeRepository nodes;
    private final DecisionFlowRootRepository roots;
    private final PlatformTransactionManager transactionManager;
    private final DecisionIdentifierGenerator identifiers;
    private final RedisResourceLockProperties lockProperties;
    private final DecisionFlowReferenceService references;
    private final DecisionFlowManagementService service;
    private final DecisionFlowVariableValidator variableValidator;
    private final DecisionFlowVersionRepository versions;

    /** 先执行纯图校验和规范化，再用稳定业务编码进入公共锁模板。 */
    @Override
    protected Saved processRequest(SaveFlowRequest request) {
        Checked checked = validator.check(request.getConfiguration());
        if (!checked.validation().valid()) {
            throw new DecisionFlowConfigurationException(Reason.INVALID_GRAPH, "流程配置不完整，请修正标记的问题",
                checked.validation().issues());
        }
        return super.processRequest(new SaveFlowRequest(checked.normalized(), request.getActorAccountId()));
    }

    /** 沿用完整逻辑 Key、普通 RLock 及既有有限等待和租约，避免改变互斥域。 */
    @Override
    protected LockCommand lockCommand(SaveFlowRequest request) {
        Save command = request.getConfiguration();
        return LockCommand.tryLocks(List.of("decision:flow:" + command.pointCode() + ":" + command.flowCode()),
            Duration.ofNanos(lockProperties.requiredWaitNanos()),
            Duration.ofNanos(lockProperties.requiredLeaseNanos()));
    }

    /** 所有资格重证与四表写入处于同一个本地短事务，完成后才释放资源锁。 */
    @Override
    protected Saved processWithinLock(SaveFlowRequest request) {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        transaction.setTimeout(5);
        return transaction.execute(status -> {
            SaveFlowContext context = prepareContext(request);
            service.save(context);
            return context.result();
        });
    }

    /** 锁根、检查基准与准确引用，构造完整的新版本上下文。 */
    private SaveFlowContext prepareContext(SaveFlowRequest request) {
        Save command = request.getConfiguration();
        if (!DecisionFlowVariableValidator.legacyIssues(command.graph()).isEmpty()) {
            throw new RiskVariableException(RiskVariableException.Reason.LEGACY_AUTHORING_NOT_SUPPORTED,
                "请刷新页面并使用统一变量条件保存");
        }
        DecisionPoint point = point(command.pointCode());
        DecisionFlowRow located = roots.findByCode(point.decisionPointId(), command.flowCode()).orElse(null);
        DecisionFlowRow previous = located == null ? null
            : roots.lockById(located.flowId()).orElseThrow(() -> new ErrorException("已定位的流程根缺失"));
        DecisionFlowVersionRow oldVersion = verifyBase(command, previous);
        Graph base = baseGraph(command.pointCode(), previous, oldVersion);
        List<Issue> issues = references.validateForSave(point.decisionPointId(), command.graph(), base);
        if (!issues.isEmpty()) {
            throw new DecisionFlowConfigurationException(Reason.REFERENCE_UNAVAILABLE, "规则版本或拒绝原因资格已变化，请重新选择", issues);
        }
        if (oldVersion != null && oldVersion.versionNo() == Integer.MAX_VALUE) {
            throw new DecisionFlowConfigurationException(Reason.VERSION_CONFLICT, "流程业务版本已达到上限", List.of());
        }
        var variableCheck = checkVariables(command.pointCode(), command.graph());
        if (!variableCheck.issues().isEmpty()) {
            throw new DecisionFlowConfigurationException(Reason.INVALID_GRAPH, "变量资格或条件不合法，请修正标记的问题",
                variableCheck.issues());
        }
        Graph configuredGraph = variableCheck.graph();
        long actorId = request.getActorAccountId();
        Instant time = clock.instant().truncatedTo(ChronoUnit.MICROS);
        long flowId = previous == null ? nextId() : previous.flowId();
        long versionId = nextId();
        int versionNo = oldVersion == null ? 1 : oldVersion.versionNo() + 1;
        var contract = CheckoutPrepareAuthoringDescriptor.v1().point();
        var version = new DecisionFlowVersionRow(versionId, flowId, versionNo, command.flowName(),
            contract.inputContractCode(), contract.inputContractVersion(), actorId, command.changeReason(), time, time);
        var root = previous != null ? previous : new DecisionFlowRow(flowId, point.decisionPointId(),
            command.flowCode(), versionId, FlowStatus.CONFIGURABLE, versionNo, null, null, time, time);
        List<DecisionFlowNodeRow> nodes =
            configuredGraph.nodes().stream()
                .map(n -> new DecisionFlowNodeRow(nextId(), versionId, n.nodeKey(), FlowNodeType.valueOf(n.type()),
                    n.name(), n.ruleSetVersionId(),
                    n.decisionCode() == null ? null : DecisionValue.valueOf(n.decisionCode()), n.reasonCode(),
                    n.reasonMessage(), n.x(), n.y(), time, time))
                .toList();
        Map<String, Long> nodeIds =
            nodes.stream().collect(Collectors.toMap(DecisionFlowNodeRow::nodeKey, DecisionFlowNodeRow::nodeId));
        List<DecisionFlowEdgeRow> edges = configuredGraph.edges().stream()
            .map(e -> new DecisionFlowEdgeRow(nextId(), versionId, e.edgeKey(), nodeIds.get(e.sourceNodeKey()),
                nodeIds.get(e.targetNodeKey()), FlowEdgeType.valueOf(e.type()), e.priority(), e.name(),
                codec.conditionJson(e.condition()), time, time))
            .toList();
        return new SaveFlowContext(root, oldVersion, version, nodes, edges);
    }

    /** 当前可编辑决策点由本库目录确认。 */
    private DecisionPoint point(String code) {
        if (!"CHECKOUT_PREPARE".equals(code)) {
            throw new DecisionConfigurationNotFoundException();
        }
        DecisionPoint point = rules.findPointByCode(code).orElseThrow(DecisionConfigurationNotFoundException::new);
        if (point.status() != DecisionPointStatus.ACTIVE) {
            throw new InvalidParameterException("决策点当前不可配置");
        }
        return point;
    }

    /** 新建编码不可复用；编辑只允许从当前头版本继续。 */
    private DecisionFlowVersionRow verifyBase(Save command, DecisionFlowRow root) {
        if (command.baseFlowVersionId() == null) {
            if (root != null) {
                throw new DecisionFlowConfigurationException(Reason.CODE_EXISTS, "流程编码已存在，删除后也不能复用", List.of());
            }
            return null;
        }
        if (root == null) {
            throw new DecisionConfigurationNotFoundException();
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

    /** 规则引用锁之后，按编码锁定变量和集合，避免资格与写入之间发生变化。 */
    private DecisionFlowVariableValidator.Result checkVariables(String pointCode, Graph graph) {
        Map<Long, DecisionRuleSetAggregate> ruleSets = referencedRules(graph);
        Set<String> codes = new TreeSet<>(VariableFlowConfiguration.references(graph));
        ruleSets.values().forEach(rule -> codes.addAll(VariableRuleConfiguration.references(rule)));
        Map<String, RiskVariable.Entry> definitions = new HashMap<>();
        Map<String, List<RiskVariable.Member>> collections = new HashMap<>();
        try {
            definitions.putAll(variables.lockDefinitions(codes));
            definitions.values().stream().filter(e -> e.definition().sourceType() == VariableSource.COLLECTION)
                .forEach(e -> collections.put(e.definition().variableCode(), variables.selectedCollection(e)));
        } catch (RiskVariableException failure) {
            return new DecisionFlowVariableValidator.Result(graph,
                List.of(new Issue(failure.reason().getCode(), null, null, "graph", failure.getMessage())));
        }
        return variableValidator.check(pointCode, graph, ruleSets, definitions, collections);
    }

    /** 新版本及明细身份必须来自本地正数 ID 生成器。 */
    private long nextId() {
        long id = identifiers.nextIdentifier();
        if (id <= 0) {
            throw new ErrorException("流程配置标识生成失败");
        }
        return id;
    }
}

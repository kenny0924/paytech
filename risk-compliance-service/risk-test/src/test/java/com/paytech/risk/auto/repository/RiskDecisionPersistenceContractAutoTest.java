package com.paytech.risk.auto.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.paytech.foundation.mybatis.typehandler.CodeEnumTypeHandler;
import com.paytech.foundation.mybatis.typehandler.InstantTypeHandler;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.reflect.RecordComponent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import javax.xml.parsers.DocumentBuilderFactory;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.mapping.ResultMapping;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.type.TypeHandlerRegistry;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/**
 * Decision 持久化的数据库无关结构合同。
 */
class RiskDecisionPersistenceContractAutoTest {

    private static final String DATAOBJECT_PACKAGE =
        "com.paytech.risk.decision.core.dal.dataobject.";
    private static final String MAPPER_PACKAGE =
        "com.paytech.risk.decision.core.dal.mapper.";
    private static final String CODE_ENUM_HANDLER =
        "com.paytech.foundation.mybatis.typehandler.CodeEnumTypeHandler";
    private static final String INSTANT_HANDLER =
        "com.paytech.foundation.mybatis.typehandler.InstantTypeHandler";

    private static final Map<String, LinkedHashMap<String, String>> DATAOBJECTS = dataObjects();
    private static final Map<String, Set<String>> NULLABLE_FIELDS = nullableFields();

    private static final Set<String> OWNER_TABLES = Set.of(
        "t_decision_point",
        "t_decision_point_version",
        "t_decision_point_input_field",
        "t_decision_point_feature",
        "t_decision_point_rule_set",
        "t_decision_point_obligation",
        "t_feature_version",
        "t_feature_dependency",
        "t_rule_set",
        "t_rule_set_version",
        "t_risk_rule",
        "t_rule_node",
        "t_rule_operand",
        "t_action_version",
        "t_action_parameter_definition",
        "t_rule_action",
        "t_rule_action_parameter",
        "t_decision_release",
        "t_release_audit_event"
    );

    @Test
    void test_dataObjectsAreExactImmutableDocumentedRecords() throws Exception {
        assertEquals(20, DATAOBJECTS.size());
        for (Map.Entry<String, LinkedHashMap<String, String>> expected : DATAOBJECTS.entrySet()) {
            Class<?> type = Class.forName(DATAOBJECT_PACKAGE + expected.getKey());
            assertTrue(type.isRecord(), expected.getKey() + " 必须是 record");
            RecordComponent[] actualComponents = type.getRecordComponents();
            assertEquals(List.copyOf(expected.getValue().keySet()), Arrays.stream(actualComponents)
                .map(RecordComponent::getName).toList(),
                expected.getKey() + " 字段顺序不匹配");
            assertEquals(List.copyOf(expected.getValue().values()), Arrays.stream(actualComponents)
                .map(component -> component.getType().getName()).toList(),
                expected.getKey() + " 字段类型不匹配");

            String source = Files.readString(sourceRoot().resolve(expected.getKey() + ".java"));
            for (String component : expected.getValue().keySet()) {
                assertTrue(Pattern.compile("(?s)/\\*\\*.*?\\*/\\s+(?:[\\w.<>]+)\\s+"
                        + Pattern.quote(component) + "\\s*(?:,|\\))").matcher(source).find(),
                    expected.getKey() + "." + component + " 缺少组件 JavaDoc");
                if (!isReferenceType(expected.getValue().get(component))) {
                    continue;
                }
                String nullGuard = "Objects.requireNonNull(" + component + ", \"" + component + "\")";
                if (NULLABLE_FIELDS.getOrDefault(expected.getKey(), Set.of()).contains(component)) {
                    assertFalse(source.contains(nullGuard), expected.getKey() + "." + component + " 必须保留 null");
                } else {
                    assertTrue(source.contains(nullGuard), expected.getKey() + "." + component
                        + " 必须在 record constructor 拒绝 null");
                }
            }
        }
    }

    @Test
    void test_mapperInterfacesExposeOnlyTheApprovedSurface() throws Exception {
        assertMapperSurface("DecisionRuleSetMapper", Set.of(
            "selectDecisionPointByCode", "selectDecisionPointById", "selectDecisionPointsByIds",
            "selectRuleSet", "selectRuleSetByCode", "lockRuleSet", "selectRuleSetsByCurrentVersions", "selectReleaseReferences",
            "lockDecisionPoint", "selectRuleSetVersion", "lockRuleSetCodeRange", "lockRuleSetVersion",
            "countMalformedRuleSets", "selectRuleSetVersions", "countRuleSetVersions",
            "selectCompanionBindingsByRuleSetVersionIds", "selectPointVersion", "selectPointVersionsByIds",
            "selectRuleSetVersionsByIds", "lockPointVersion",
            "selectPointInputFields", "selectPointRuleSets", "selectPointRuleSetsByPointVersionIds",
            "selectPointFeatures", "selectPointObligations", "selectRules", "selectNodes", "selectOperands",
            "selectRuleActions", "selectActionVersions", "selectActionParameterDefinitions",
            "selectRuleActionParameters", "selectFeatureVersions", "selectFeatureDependencies",
            "insertRuleSet", "insertRuleSetVersion",
            "insertRules", "insertNodes", "insertOperands", "insertRuleActions", "insertRuleActionParameters", "retireRuleSetVersion",
            "retirePointVersion", "freezeRuleSetVersion", "freezePointVersion", "advanceRuleSet", "deleteRuleSet"
        ));
        assertMapperSurface("DecisionReleaseMapper", Set.of(
            "selectRelease", "lockRelease", "selectReleases", "countReleases",
            "selectReleaseAuditEvents", "selectReleaseAuditEventsByReleaseIds",
            "countNonCanonicalReleaseNumbers", "selectMaxReleaseNo", "insertRelease",
            "insertReleaseAuditEvent", "insertReleaseAuditEvents", "moveReleaseToValidating",
            "moveReleaseToRejected"
        ));
    }

    @Test
    void test_lockedResourceDisappearanceAndUnexpectedCasCountAreInternalCorruption() throws Exception {
        for (String implementation : List.of("DecisionRuleSetRepositoryImpl", "DecisionReleaseRepositoryImpl")) {
            Class<?> type = Class.forName(
                "com.paytech.risk.decision.domainservice.repository.impl." + implementation);

            Method requireLocked = type.getDeclaredMethod("requireLocked", Object.class, String.class);
            requireLocked.setAccessible(true);
            InvocationTargetException missing = assertThrows(InvocationTargetException.class,
                () -> requireLocked.invoke(null, null, "missing"));
            assertEquals("com.paytech.foundation.common.error.ErrorException",
                missing.getCause().getClass().getName(), implementation + " 锁中资源消失必须为 500 corruption");

            Method requireCas = type.getDeclaredMethod("requireCas", int.class, String.class);
            requireCas.setAccessible(true);
            for (int affectedRows : List.of(0, 2)) {
                InvocationTargetException failure = assertThrows(InvocationTargetException.class,
                    () -> requireCas.invoke(null, affectedRows, "cas"));
                assertEquals("com.paytech.foundation.common.error.ErrorException",
                    failure.getCause().getClass().getName(), implementation
                        + " 锁后 CAS 非 1 行必须为 500 corruption");
            }
        }

        String ruleSetSource = Files.readString(Path.of(System.getProperty("risk.reactor.root"),
            "risk-decision", "src", "main", "java", "com", "paytech", "risk", "decision",
            "domainservice", "repository", "impl", "DecisionRuleSetRepositoryImpl.java"));
        assertTrue(ruleSetSource.contains("requireLocked(mapper.lockDecisionPoint"));
        assertTrue(ruleSetSource.contains("requireLocked(mapper.lockRuleSetVersion"));
        assertTrue(ruleSetSource.contains("requireLocked(mapper.lockPointVersion"));
        String releaseSource = Files.readString(Path.of(System.getProperty("risk.reactor.root"),
            "risk-decision", "src", "main", "java", "com", "paytech", "risk", "decision",
            "domainservice", "repository", "impl", "DecisionReleaseRepositoryImpl.java"));
        assertTrue(releaseSource.contains("requireLocked(releaseMapper.lockRelease"));
    }

    @Test
    void test_ruleSetXmlLoadsAndMatchesTheExactContract() throws Exception {
        MapperXml xml = mapperXml("DecisionRuleSetMapper");
        assertEquals(Set.of(
            "RuleSetResultMap", "RuleSetReleaseReferenceResultMap", "DecisionPointResultMap", "DecisionPointVersionResultMap", "DecisionPointInputFieldResultMap",
            "DecisionPointFeatureResultMap", "DecisionPointRuleSetResultMap", "DecisionPointObligationResultMap",
            "FeatureVersionResultMap", "FeatureDependencyResultMap", "RuleSetVersionResultMap",
            "RiskRuleResultMap", "RuleNodeResultMap", "RuleOperandResultMap", "ActionVersionResultMap",
            "ActionParameterDefinitionResultMap", "RuleActionResultMap", "RuleActionParameterResultMap"
        ), xml.ids("resultMap"));
        assertEquals(Set.of("RuleSetListFilter", "RuleSetColumns"), xml.ids("sql"));
        assertEquals(Set.of(
            "selectDecisionPointByCode", "selectDecisionPointById", "selectDecisionPointsByIds",
            "selectRuleSet", "selectRuleSetByCode", "lockRuleSet", "selectRuleSetsByCurrentVersions", "selectReleaseReferences",
            "lockDecisionPoint", "selectRuleSetVersion", "lockRuleSetCodeRange", "lockRuleSetVersion",
            "countMalformedRuleSets", "selectRuleSetVersions", "countRuleSetVersions",
            "selectCompanionBindingsByRuleSetVersionIds", "selectPointVersion", "selectPointVersionsByIds",
            "selectRuleSetVersionsByIds", "lockPointVersion",
            "selectPointInputFields", "selectPointRuleSets", "selectPointRuleSetsByPointVersionIds",
            "selectPointFeatures", "selectPointObligations", "selectRules", "selectNodes", "selectOperands",
            "selectRuleActions", "selectActionVersions", "selectActionParameterDefinitions",
            "selectRuleActionParameters", "selectFeatureVersions", "selectFeatureDependencies"
        ), xml.ids("select"));
        assertEquals(Set.of("insertRuleSet", "insertRuleSetVersion",
            "insertRules", "insertNodes", "insertOperands", "insertRuleActions", "insertRuleActionParameters"),
            xml.ids("insert"));
        assertEquals(Set.of("retireRuleSetVersion", "retirePointVersion", "freezeRuleSetVersion",
            "freezePointVersion", "advanceRuleSet", "deleteRuleSet"), xml.ids("update"));

        xml.assertSharedFilter("selectRuleSetVersions", "countRuleSetVersions", "RuleSetListFilter");
        assertContainsNormalized(xml.statement("RuleSetListFilter"),
            "from t_rule_set rs join t_rule_set_version rsv on rsv.rule_set_version_id = rs.current_rule_set_version_id join t_decision_point dp on dp.decision_point_id = rs.decision_point_id where rs.status = 'CONFIGURABLE'");
        assertContainsNormalized(xml.statement("selectRuleSetVersions"),
            "order by rsv.create_time desc, rsv.rule_set_version_id desc limit #{limit} offset #{offset}");
        assertContainsNormalized(xml.statement("lockRuleSetCodeRange"),
            "from t_rule_set_version force index (uk_rule_set_ver)");
        assertContainsNormalized(xml.statement("freezeRuleSetVersion"),
            "where rule_set_version_id = #{rulesetversionid} and status = #{expectedstatus");
        assertContainsNormalized(xml.statement("freezeRuleSetVersion"),
            "and content_hash = #{expectedcontenthash}");
        assertContainsNormalized(xml.statement("freezePointVersion"),
            "where point_version_id = #{pointversionid} and status = #{expectedstatus");
        assertContainsNormalized(xml.statement("freezePointVersion"),
            "and content_hash = #{expectedcontenthash}");
        xml.assertLocks("lockDecisionPoint", "lockRuleSetCodeRange", "lockRuleSetVersion", "lockPointVersion");
        xml.assertBatch("insertRules", "values");
        xml.assertBatch("insertNodes", "values");
        xml.assertBatch("insertOperands", "values");
        xml.assertBatch("insertRuleActions", "values");
        xml.assertAllSelectsDisableCache();
        xml.assertResultMapsMatchDataObjects();
        xml.assertEveryResultMapUsesJavaTypeBoundEnumHandlers();
        xml.assertParametersUseRegisteredEnumsAndExplicitInstants();
        xml.assertSafeSql();
    }

    @Test
    void test_releaseXmlLoadsAndMatchesTheExactContract() throws Exception {
        MapperXml xml = mapperXml("DecisionReleaseMapper");
        assertEquals(Set.of("DecisionReleaseResultMap", "ReleaseAuditEventResultMap"),
            xml.ids("resultMap"));
        assertEquals(Set.of("ReleaseListFilter"), xml.ids("sql"));
        assertEquals(Set.of("selectRelease", "lockRelease", "selectReleases", "countReleases",
            "selectReleaseAuditEvents", "selectReleaseAuditEventsByReleaseIds",
            "countNonCanonicalReleaseNumbers", "selectMaxReleaseNo"), xml.ids("select"));
        assertEquals(Set.of("insertRelease", "insertReleaseAuditEvent", "insertReleaseAuditEvents"),
            xml.ids("insert"));
        assertEquals(Set.of("moveReleaseToValidating", "moveReleaseToRejected"), xml.ids("update"));

        xml.assertSharedFilter("selectReleases", "countReleases", "ReleaseListFilter");
        assertContainsNormalized(xml.statement("ReleaseListFilter"),
            "from t_decision_release dr join t_decision_point dp on dp.decision_point_id = dr.decision_point_id where dr.environment_code = 'test'");
        assertContainsNormalized(xml.statement("selectReleases"),
            "order by dr.create_time desc, dr.release_id desc limit #{limit} offset #{offset}");
        String invalidCount = normalized(xml.statement("countNonCanonicalReleaseNumbers"));
        assertTrue(invalidCount.contains("release_no not regexp '^[1-9][0-9]{0,9}$'"));
        assertTrue(invalidCount.contains("char_length(release_no) = 10"));
        assertTrue(invalidCount.contains("release_no > '2147483647'"));
        assertFalse(invalidCount.contains("decision_point_id"));
        assertFalse(invalidCount.contains("environment_code"));
        String maximum = normalized(xml.statement("selectMaxReleaseNo"));
        assertTrue(maximum.contains("coalesce(max(cast(release_no as unsigned)), 0)"));
        assertFalse(maximum.contains("where"));
        assertContainsNormalized(xml.statement("moveReleaseToValidating"),
            "where release_id = #{releaseid} and status = #{expectedstatus");
        assertContainsNormalized(xml.statement("moveReleaseToValidating"),
            "and version = #{expectedversion}");
        assertContainsNormalized(xml.statement("moveReleaseToRejected"),
            "where release_id = #{releaseid} and status = #{expectedstatus");
        assertContainsNormalized(xml.statement("moveReleaseToRejected"),
            "and version = #{expectedversion}");
        xml.assertLocks("lockRelease");
        xml.assertBatch("insertReleaseAuditEvents", "values");
        xml.assertAllSelectsDisableCache();
        xml.assertResultMapsMatchDataObjects();
        xml.assertEveryResultMapUsesJavaTypeBoundEnumHandlers();
        xml.assertParametersUseRegisteredEnumsAndExplicitInstants();
        xml.assertSafeSql();
    }

    @Test
    void test_xmlTouchesExactlyTheDecisionOwnerTables() throws Exception {
        String allXml = mapperXml("DecisionRuleSetMapper").raw + mapperXml("DecisionReleaseMapper").raw;
        Matcher matcher = Pattern.compile("\\bt_[a-z_]+\\b").matcher(allXml);
        Set<String> actual = new LinkedHashSet<>();
        while (matcher.find()) {
            actual.add(matcher.group());
        }
        assertEquals(OWNER_TABLES, actual);
        assertFalse(allXml.contains("t_command_idempotency"));
        assertFalse(allXml.contains("t_decision_point_limit_binding"));
        assertFalse(allXml.contains("t_release_shadow_run"));
    }

    private void assertMapperSurface(String simpleName, Set<String> expectedMethods) throws Exception {
        Class<?> mapper = Class.forName(MAPPER_PACKAGE + simpleName);
        assertTrue(mapper.isInterface());
        Set<String> actualMethods = Arrays.stream(mapper.getDeclaredMethods())
            .map(Method::getName).collect(Collectors.toSet());
        assertEquals(expectedMethods, actualMethods);
        for (Method method : mapper.getDeclaredMethods()) {
            assertEquals(0, method.getAnnotations().length, method + " 禁止 SQL/Provider 注解");
            for (Parameter parameter : method.getParameters()) {
                assertEquals(1, parameter.getAnnotations().length, parameter + " 必须且只能声明一个 @Param");
                Param binding = parameter.getAnnotation(Param.class);
                assertNotNull(binding, parameter + " 必须声明 @Param");
                assertFalse(binding.value().isBlank(), parameter + " 的 @Param 绑定名不得为空");
                assertEquals(parameter.getName(), binding.value(), parameter + " 的绑定名必须与 Java 参数名一致");
            }
        }
    }

    private MapperXml mapperXml(String simpleName) throws Exception {
        String resource = "mapper/decision/" + simpleName + ".xml";
        Class<?> mapperType = Class.forName(MAPPER_PACKAGE + simpleName);
        Configuration configuration = new Configuration();
        registerConcreteTypeHandlers(configuration.getTypeHandlerRegistry());
        configuration.addMapper(mapperType);
        try (InputStream input = mapperType.getClassLoader().getResourceAsStream(resource)) {
            assertNotNull(input, resource + " 不存在");
            new XMLMapperBuilder(input, configuration, resource, configuration.getSqlFragments()).parse();
        }
        Path path = Path.of(System.getProperty("risk.reactor.root"), "risk-decision", "src", "main",
            "resources", "mapper", "decision", simpleName + ".xml");
        String raw = Files.readString(path).toLowerCase();
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", false);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        Document document = factory.newDocumentBuilder().parse(path.toFile());
        assertEquals(MAPPER_PACKAGE + simpleName, document.getDocumentElement().getAttribute("namespace"));
        return new MapperXml(configuration, document, raw);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void registerConcreteTypeHandlers(TypeHandlerRegistry registry) throws Exception {
        registry.register(Instant.class, InstantTypeHandler.class);
        Set<Class<?>> registered = new LinkedHashSet<>();
        for (Map.Entry<String, LinkedHashMap<String, String>> value : DATAOBJECTS.entrySet()) {
            Class<?> dataObject = Class.forName(DATAOBJECT_PACKAGE + value.getKey());
            for (RecordComponent component : dataObject.getRecordComponents()) {
                Class<?> type = component.getType();
                if (type.isEnum() && registered.add(type)) {
                    registry.register((Class)type, CodeEnumTypeHandler.class);
                }
            }
        }
    }

    private Path sourceRoot() {
        return Path.of(System.getProperty("risk.reactor.root"), "risk-decision", "src", "main", "java",
            "com", "paytech", "risk", "decision", "core", "dal", "dataobject");
    }

    private static void assertContainsNormalized(String actual, String expected) {
        assertTrue(normalized(actual).contains(normalized(expected)), () -> "SQL 缺少: " + expected + "\n" + actual);
    }

    private static String normalized(String value) {
        return value.toLowerCase().replaceAll("\\s+", " ").trim();
    }

    private static Map<String, LinkedHashMap<String, String>> dataObjects() {
        LinkedHashMap<String, LinkedHashMap<String, String>> records = new LinkedHashMap<>();
        add(records, "RuleSet", "ruleSetId:long", "decisionPointId:long", "ruleSetCode:java.lang.String",
            "currentRuleSetVersionId:long", "status:com.paytech.risk.decision.domain.enums.RuleSetManagementStatus",
            "version:long", "deletedBy:java.lang.Long", "deletedTime:java.time.Instant", "deleteReasonCode:java.lang.String",
            "deleteTriggerId:java.lang.String", "createTime:java.time.Instant", "updateTime:java.time.Instant");
        add(records, "RuleSetReleaseReference", "releaseId:long", "releaseDecisionPointId:long",
            "releaseStatus:com.paytech.risk.decision.domain.enums.DecisionReleaseStatus", "activeSlot:java.lang.Integer",
            "pointVersionId:long", "pointDecisionPointId:java.lang.Long",
            "pointStatus:com.paytech.risk.decision.domain.enums.DecisionPointVersionStatus", "bindingId:java.lang.Long",
            "ruleSetVersionId:java.lang.Long", "ruleDecisionPointId:java.lang.Long", "ruleSetCode:java.lang.String",
            "ruleVersionStatus:com.paytech.risk.decision.domain.enums.RuleSetVersionStatus", "ruleSetId:java.lang.Long",
            "managementStatus:com.paytech.risk.decision.domain.enums.RuleSetManagementStatus");
        add(records, "DecisionPoint", "decisionPointId:long", "pointCode:java.lang.String",
            "pointName:java.lang.String", "businessDomain:com.paytech.risk.decision.domain.enums.DecisionBusinessDomain",
            "status:com.paytech.risk.decision.domain.enums.DecisionPointStatus", "createTime:java.time.Instant",
            "updateTime:java.time.Instant");
        add(records, "DecisionPointVersion", "pointVersionId:long", "decisionPointId:long", "versionNo:int",
            "inputContractCode:java.lang.String", "inputContractVersion:int", "outputContractCode:java.lang.String",
            "defaultDecision:com.paytech.risk.decision.domain.enums.DecisionValue",
            "evaluationStrategy:com.paytech.risk.decision.domain.enums.EvaluationStrategy",
            "failureStrategy:com.paytech.risk.decision.domain.enums.FailureStrategy", "timeoutMillis:int",
            "status:com.paytech.risk.decision.domain.enums.DecisionPointVersionStatus",
            "contentHash:java.lang.String", "createTime:java.time.Instant", "updateTime:java.time.Instant");
        add(records, "DecisionPointInputField", "inputFieldId:long", "pointVersionId:long",
            "fieldCode:java.lang.String", "valueType:com.paytech.risk.decision.domain.enums.DecisionValueType",
            "requiredFlag:boolean", "dataClassification:com.paytech.risk.decision.domain.enums.DataClassification",
            "ruleReferenceFlag:boolean", "actionParameterFlag:boolean", "createTime:java.time.Instant");
        add(records, "DecisionPointFeature", "bindingId:long", "pointVersionId:long", "featureVersionId:long",
            "sequenceNo:int", "createTime:java.time.Instant");
        add(records, "DecisionPointRuleSet", "bindingId:long", "pointVersionId:long", "ruleSetVersionId:long",
            "sequenceNo:int", "createTime:java.time.Instant");
        add(records, "DecisionPointObligation", "obligationId:long", "pointVersionId:long",
            "triggerDecision:com.paytech.risk.decision.domain.enums.DecisionValue",
            "obligationType:com.paytech.risk.decision.domain.enums.DecisionObligationType",
            "actionVersionId:java.lang.Long", "sequenceNo:int",
            "failurePolicy:com.paytech.risk.decision.domain.enums.DecisionObligationFailurePolicy",
            "enabledFlag:boolean", "createTime:java.time.Instant");
        add(records, "FeatureVersion", "featureVersionId:long", "featureCode:java.lang.String",
            "featureName:java.lang.String", "businessCategory:java.lang.String", "versionNo:int",
            "resultValueType:com.paytech.risk.decision.domain.enums.DecisionValueType",
            "handlerCode:java.lang.String", "implementationVersion:java.lang.String",
            "implementationHash:java.lang.String", "timeoutMillis:int",
            "status:com.paytech.risk.decision.domain.enums.ExecutionVersionStatus", "createTime:java.time.Instant",
            "updateTime:java.time.Instant");
        add(records, "FeatureDependency", "dependencyId:long", "featureVersionId:long",
            "dependencyFeatureVersionId:long", "sequenceNo:int", "requiredFlag:boolean",
            "createTime:java.time.Instant");
        add(records, "RuleSetVersion", "ruleSetVersionId:long", "decisionPointId:long",
            "ruleSetCode:java.lang.String", "ruleSetName:java.lang.String", "versionNo:int",
            "status:com.paytech.risk.decision.domain.enums.RuleSetVersionStatus", "contentHash:java.lang.String",
            "createdBy:long", "changeReason:java.lang.String", "frozenBy:java.lang.Long",
            "frozenTime:java.time.Instant", "createTime:java.time.Instant", "updateTime:java.time.Instant", "contentFormat:java.lang.String", "inputContractCode:java.lang.String", "inputContractVersion:int");
        add(records, "RiskRule", "ruleId:long", "ruleSetVersionId:long", "ruleCode:java.lang.String",
            "ruleName:java.lang.String", "priorityNo:int", "enabledFlag:boolean", "reasonCode:java.lang.String",
            "createTime:java.time.Instant", "updateTime:java.time.Instant", "reasonMessage:java.lang.String");
        add(records, "RuleNode", "nodeId:long", "ruleId:long", "parentNodeId:java.lang.Long",
            "nodeType:com.paytech.risk.decision.domain.enums.NodeType",
            "groupOperator:com.paytech.risk.decision.domain.enums.GroupOperator",
            "comparisonOperator:com.paytech.risk.decision.domain.enums.ComparisonOperator", "sequenceNo:int",
            "createTime:java.time.Instant", "updateTime:java.time.Instant");
        add(records, "RuleOperand", "operandId:long", "nodeId:long",
            "operandRole:com.paytech.risk.decision.domain.enums.OperandRole", "valueSequence:int",
            "sourceType:com.paytech.risk.decision.domain.enums.OperandSource", "featureVersionId:java.lang.Long",
            "fieldCode:java.lang.String", "valueType:com.paytech.risk.decision.domain.enums.DecisionValueType",
            "stringValue:java.lang.String", "numberValue:java.math.BigDecimal", "booleanValue:java.lang.Boolean",
            "datetimeValue:java.time.Instant", "durationValue:java.lang.Long", "createTime:java.time.Instant",
            "updateTime:java.time.Instant", "variableCode:java.lang.String",
            "numericUnit:com.paytech.risk.decision.domain.enums.NumericUnit",
            "currencyCode:java.lang.String", "currencyVariableCode:java.lang.String");
        add(records, "ActionVersion", "actionVersionId:long", "actionCode:java.lang.String",
            "actionName:java.lang.String", "actionCategory:com.paytech.risk.decision.domain.enums.ActionCategory",
            "versionNo:int", "handlerCode:java.lang.String", "implementationVersion:java.lang.String",
            "implementationHash:java.lang.String", "terminalFlag:boolean",
            "decisionCode:com.paytech.risk.decision.domain.enums.DecisionValue",
            "mergePolicy:com.paytech.risk.decision.domain.enums.MergePolicy", "shadowSupportedFlag:boolean",
            "status:com.paytech.risk.decision.domain.enums.ExecutionVersionStatus", "createTime:java.time.Instant",
            "updateTime:java.time.Instant");
        add(records, "ActionParameterDefinition", "parameterDefinitionId:long", "actionVersionId:long",
            "parameterCode:java.lang.String", "valueType:com.paytech.risk.decision.domain.enums.DecisionValueType",
            "requiredFlag:boolean", "allowedSourceType:com.paytech.risk.decision.domain.enums.OperandSource",
            "targetParameterFlag:boolean", "createTime:java.time.Instant");
        add(records, "RuleAction", "ruleActionId:long", "ruleId:long", "actionVersionId:long",
            "sequenceNo:int", "createTime:java.time.Instant", "targetBindingCode:java.lang.String");
        add(records, "RuleActionParameter", "parameterId:long", "ruleActionId:long",
            "parameterDefinitionId:long", "valueSequence:int",
            "sourceType:com.paytech.risk.decision.domain.enums.OperandSource", "featureVersionId:java.lang.Long",
            "fieldCode:java.lang.String", "stringValue:java.lang.String", "numberValue:java.math.BigDecimal",
            "booleanValue:java.lang.Boolean", "datetimeValue:java.time.Instant", "durationValue:java.lang.Long",
            "createTime:java.time.Instant", "variableCode:java.lang.String");
        add(records, "DecisionRelease", "releaseId:long", "releaseNo:java.lang.String", "decisionPointId:long",
            "pointVersionId:long", "environmentCode:com.paytech.risk.decision.domain.enums.DecisionEnvironment",
            "status:com.paytech.risk.decision.domain.enums.DecisionReleaseStatus", "activeSlot:java.lang.Boolean",
            "contentHash:java.lang.String", "createdBy:long", "activatedTime:java.time.Instant",
            "supersededTime:java.time.Instant", "version:int", "createTime:java.time.Instant",
            "updateTime:java.time.Instant");
        add(records, "ReleaseAuditEvent", "auditEventId:long", "releaseId:long",
            "eventType:com.paytech.risk.decision.domain.enums.ReleaseAuditEventType", "eventTime:java.time.Instant",
            "checkCode:java.lang.String", "checkStatus:com.paytech.risk.decision.domain.enums.StaticCheckStatus",
            "severity:com.paytech.risk.decision.domain.enums.StaticCheckSeverity", "errorCode:java.lang.String",
            "errorSummary:java.lang.String", "shadowRunId:java.lang.Long",
            "approvalRequestId:java.lang.String",
            "approvalStatus:com.paytech.risk.decision.domain.enums.ReleaseApprovalStatus",
            "reviewerId:java.lang.Long", "reviewComment:java.lang.String",
            "fromStatus:com.paytech.risk.decision.domain.enums.DecisionReleaseStatus",
            "toStatus:com.paytech.risk.decision.domain.enums.DecisionReleaseStatus",
            "reasonCode:java.lang.String", "triggerType:java.lang.String", "triggerId:java.lang.String",
            "actorId:java.lang.Long", "aggregateVersion:java.lang.Integer", "createTime:java.time.Instant");
        return Map.copyOf(records);
    }

    private static void add(Map<String, LinkedHashMap<String, String>> records, String name, String... fields) {
        LinkedHashMap<String, String> components = new LinkedHashMap<>();
        for (String field : fields) {
            String[] parts = field.split(":", 2);
            components.put(parts[0], parts[1]);
        }
        records.put(name, components);
    }

    private static boolean isReferenceType(String typeName) {
        return !Set.of("long", "int", "boolean").contains(typeName);
    }

    private static Map<String, Set<String>> nullableFields() {
        return Map.ofEntries(
            Map.entry("RuleSet", Set.of("deletedBy", "deletedTime", "deleteReasonCode", "deleteTriggerId")),
            Map.entry("RuleSetReleaseReference", Set.of("activeSlot", "pointDecisionPointId", "pointStatus", "bindingId",
                "ruleSetVersionId", "ruleDecisionPointId", "ruleSetCode", "ruleVersionStatus", "ruleSetId", "managementStatus")),
            Map.entry("DecisionPointObligation", Set.of("actionVersionId")),
            Map.entry("RuleSetVersion", Set.of("frozenBy", "frozenTime")),
            Map.entry("RiskRule", Set.of("reasonCode", "reasonMessage")),
            Map.entry("RuleAction", Set.of("targetBindingCode")),
            Map.entry("RuleNode", Set.of("parentNodeId", "groupOperator", "comparisonOperator")),
            Map.entry("RuleOperand", Set.of("variableCode", "numericUnit", "currencyCode", "currencyVariableCode", "featureVersionId", "fieldCode", "stringValue",
                "numberValue", "booleanValue", "datetimeValue", "durationValue")),
            Map.entry("ActionVersion", Set.of("decisionCode", "handlerCode", "implementationVersion", "implementationHash")),
            Map.entry("RuleActionParameter", Set.of("variableCode", "featureVersionId", "fieldCode", "stringValue",
                "numberValue", "booleanValue", "datetimeValue", "durationValue")),
            Map.entry("DecisionRelease", Set.of("activeSlot", "activatedTime", "supersededTime")),
            Map.entry("ReleaseAuditEvent", Set.of("checkCode", "checkStatus", "severity", "errorCode",
                "errorSummary", "shadowRunId", "approvalRequestId", "approvalStatus", "reviewerId",
                "reviewComment", "fromStatus", "toStatus", "reasonCode", "triggerType", "triggerId",
                "actorId", "aggregateVersion"))
        );
    }

    private static final class MapperXml {
        private final Configuration configuration;
        private final Document document;
        private final String raw;

        private MapperXml(Configuration configuration, Document document, String raw) {
            this.configuration = configuration;
            this.document = document;
            this.raw = raw;
        }

        private Set<String> ids(String elementName) {
            NodeList elements = document.getElementsByTagName(elementName);
            Set<String> values = new LinkedHashSet<>();
            for (int index = 0; index < elements.getLength(); index++) {
                values.add(((Element) elements.item(index)).getAttribute("id"));
            }
            return values;
        }

        private Element element(String id) {
            for (String elementName : List.of("sql", "select", "insert", "update", "resultMap")) {
                NodeList elements = document.getElementsByTagName(elementName);
                for (int index = 0; index < elements.getLength(); index++) {
                    Element element = (Element) elements.item(index);
                    if (id.equals(element.getAttribute("id"))) {
                        return element;
                    }
                }
            }
            throw new AssertionError("XML 元素不存在: " + id);
        }

        private String statement(String id) {
            return element(id).getTextContent();
        }

        private void assertSharedFilter(String items, String count, String fragment) {
            for (String statement : List.of(items, count)) {
                Element value = element(statement);
                NodeList includes = value.getElementsByTagName("include");
                assertEquals(1, includes.getLength(), statement + " 必须只复用一个过滤片段");
                assertEquals(fragment, ((Element) includes.item(0)).getAttribute("refid"));
            }
        }

        private void assertLocks(String... statementIds) {
            for (String statementId : statementIds) {
                Element statement = element(statementId);
                assertEquals("false", statement.getAttribute("useCache"));
                assertEquals("true", statement.getAttribute("flushCache"));
                assertTrue(normalized(statement.getTextContent()).endsWith("for update"), statementId);
            }
        }

        private void assertBatch(String statementId, String collection) {
            Element statement = element(statementId);
            NodeList foreach = statement.getElementsByTagName("foreach");
            assertEquals(1, foreach.getLength(), statementId + " 必须使用单一批量 foreach");
            assertEquals(collection, ((Element) foreach.item(0)).getAttribute("collection"));
            assertTrue(normalized(statement.getTextContent()).contains("insert into t_"));
            assertTrue(statement.getTextContent().contains("("), statementId + " 必须显式枚举列");
        }

        private void assertAllSelectsDisableCache() {
            NodeList selects = document.getElementsByTagName("select");
            for (int index = 0; index < selects.getLength(); index++) {
                Element select = (Element) selects.item(index);
                assertEquals("false", select.getAttribute("useCache"), select.getAttribute("id"));
            }
        }

        private void assertEveryResultMapUsesJavaTypeBoundEnumHandlers() throws Exception {
            NodeList maps = document.getElementsByTagName("resultMap");
            for (int index = 0; index < maps.getLength(); index++) {
                Element map = (Element) maps.item(index);
                for (Element argument : constructorArguments(map)) {
                    String javaType = argument.getAttribute("javaType");
                    String handler = argument.getAttribute("typeHandler");
                    if ("java.time.Instant".equals(javaType)) {
                        assertEquals(INSTANT_HANDLER, handler, map.getAttribute("id") + "." + argument.getAttribute("name"));
                    }
                    if (javaType.startsWith("com.paytech.risk.decision.domain.enums.")) {
                        assertEquals("", handler,
                            map.getAttribute("id") + "." + argument.getAttribute("name")
                                + " 必须由 javaType 对应的注册实例解析，不能显式引用有状态泛型 Handler");
                        Class<?> enumType = Class.forName(javaType);
                        ResultMapping runtimeMapping = configuration.getResultMap(
                                document.getDocumentElement().getAttribute("namespace") + "."
                                    + map.getAttribute("id"))
                            .getResultMappings().stream()
                            .filter(candidate -> argument.getAttribute("name").equals(candidate.getProperty()))
                            .findFirst()
                            .orElseThrow();
                        assertSame(configuration.getTypeHandlerRegistry().getTypeHandler(enumType),
                            runtimeMapping.getTypeHandler(),
                            map.getAttribute("id") + "." + argument.getAttribute("name")
                                + " 必须绑定该 javaType 的注册 Handler 实例");
                    }
                }
            }
        }

        private void assertResultMapsMatchDataObjects() {
            NodeList maps = document.getElementsByTagName("resultMap");
            for (int index = 0; index < maps.getLength(); index++) {
                Element map = (Element)maps.item(index);
                String type = map.getAttribute("type");
                assertTrue(type.startsWith(DATAOBJECT_PACKAGE), map.getAttribute("id") + " 类型越过 Owner 边界");
                String simpleName = type.substring(DATAOBJECT_PACKAGE.length());
                LinkedHashMap<String, String> expected = DATAOBJECTS.get(simpleName);
                assertNotNull(expected, map.getAttribute("id") + " 映射了未批准 DataObject");
                assertEquals(simpleName + "ResultMap", map.getAttribute("id"));

                List<Element> arguments = constructorArguments(map);
                assertEquals(List.copyOf(expected.keySet()), arguments.stream()
                    .map(value -> value.getAttribute("name")).toList(),
                    map.getAttribute("id") + " 字段顺序不匹配");
                assertEquals(expected.values().stream().map(MapperXml::xmlJavaType).toList(), arguments.stream()
                    .map(value -> value.getAttribute("javaType")).toList(),
                    map.getAttribute("id") + " Java 类型不匹配");
                assertEquals(expected.keySet().stream().map(MapperXml::columnName).toList(), arguments.stream()
                    .map(value -> value.getAttribute("column")).toList(),
                    map.getAttribute("id") + " 列顺序不匹配");
            }
        }

        private static List<Element> constructorArguments(Element map) {
            NodeList constructors = map.getElementsByTagName("constructor");
            assertEquals(1, constructors.getLength(), map.getAttribute("id") + " 必须只有一个 constructor");
            NodeList children = constructors.item(0).getChildNodes();
            java.util.ArrayList<Element> arguments = new java.util.ArrayList<>();
            for (int index = 0; index < children.getLength(); index++) {
                if (children.item(index) instanceof Element element
                    && ("idArg".equals(element.getTagName()) || "arg".equals(element.getTagName()))) {
                    arguments.add(element);
                }
            }
            return List.copyOf(arguments);
        }

        private static String xmlJavaType(String javaType) {
            return switch (javaType) {
                case "long" -> "_long";
                case "int" -> "_int";
                case "boolean" -> "_boolean";
                default -> javaType;
            };
        }

        private static String columnName(String property) {
            if ("timeoutMillis".equals(property)) {
                return "timeout_ms";
            }
            return property.replaceAll("([a-z0-9])([A-Z])", "$1_$2").toLowerCase();
        }

        private void assertParametersUseRegisteredEnumsAndExplicitInstants() {
            assertFalse(raw.contains(CODE_ENUM_HANDLER.toLowerCase()),
                "枚举参数必须由 javaType 或运行时参数类型选择具体注册实例");
            Set<String> instantProperties = Set.of(
                "createtime", "updatetime", "frozentime", "datetimevalue", "eventtime",
                "activatedtime", "supersededtime"
            );
            Matcher placeholders = Pattern.compile("#\\{([^}]*)}").matcher(raw);
            while (placeholders.find()) {
                String body = placeholders.group(1).toLowerCase();
                String path = body.split(",", 2)[0].trim();
                String property = path.substring(path.lastIndexOf('.') + 1);
                if (instantProperties.contains(property)) {
                    assertTrue(body.contains("typehandler=" + INSTANT_HANDLER.toLowerCase()),
                        placeholders.group() + " 缺少 UTC Instant TypeHandler");
                }
            }
            assertNullableJdbcTypesByStatement();
        }

        private void assertNullableJdbcTypesByStatement() {
            Map<String, Map<String, String>> expected = Map.ofEntries(
                Map.entry("insertRuleSetVersion", Map.of(
                    "frozenBy", "BIGINT", "frozenTime", "TIMESTAMP")),
                Map.entry("insertRules", Map.of("reasonCode", "VARCHAR")),
                Map.entry("insertNodes", Map.of(
                    "parentNodeId", "BIGINT", "groupOperator", "VARCHAR",
                    "comparisonOperator", "VARCHAR")),
                Map.entry("insertOperands", Map.ofEntries(
                    Map.entry("featureVersionId", "BIGINT"), Map.entry("fieldCode", "VARCHAR"),
                    Map.entry("stringValue", "VARCHAR"), Map.entry("numberValue", "DECIMAL"),
                    Map.entry("booleanValue", "BOOLEAN"), Map.entry("datetimeValue", "TIMESTAMP"),
                    Map.entry("durationValue", "BIGINT"))),
                Map.entry("insertRuleActionParameters", Map.ofEntries(
                    Map.entry("featureVersionId", "BIGINT"), Map.entry("fieldCode", "VARCHAR"),
                    Map.entry("stringValue", "VARCHAR"), Map.entry("numberValue", "DECIMAL"),
                    Map.entry("booleanValue", "BOOLEAN"), Map.entry("datetimeValue", "TIMESTAMP"),
                    Map.entry("durationValue", "BIGINT"))),
                Map.entry("insertRelease", Map.of(
                    "activeSlot", "BOOLEAN", "activatedTime", "TIMESTAMP",
                    "supersededTime", "TIMESTAMP")),
                Map.entry("insertReleaseAuditEvent", auditNullableJdbcTypes()),
                Map.entry("insertReleaseAuditEvents", auditNullableJdbcTypes())
            );
            Set<String> insertIds = ids("insert");
            for (Map.Entry<String, Map<String, String>> statement : expected.entrySet()) {
                if (!insertIds.contains(statement.getKey())) {
                    continue;
                }
                String sql = statement(statement.getKey()).toLowerCase();
                for (Map.Entry<String, String> field : statement.getValue().entrySet()) {
                    String expectedBinding = "#{value." + field.getKey().toLowerCase()
                        + ",jdbctype=" + field.getValue().toLowerCase();
                    assertTrue(sql.replaceAll("\\s+", "").contains(expectedBinding),
                        statement.getKey() + "." + field.getKey()
                            + " 缺少或使用了错误的 nullable jdbcType");
                }
            }
        }

        private static Map<String, String> auditNullableJdbcTypes() {
            return Map.ofEntries(
                Map.entry("checkCode", "VARCHAR"), Map.entry("checkStatus", "VARCHAR"),
                Map.entry("severity", "VARCHAR"), Map.entry("errorCode", "VARCHAR"),
                Map.entry("errorSummary", "VARCHAR"), Map.entry("shadowRunId", "BIGINT"),
                Map.entry("approvalRequestId", "VARCHAR"), Map.entry("approvalStatus", "VARCHAR"),
                Map.entry("reviewerId", "BIGINT"), Map.entry("reviewComment", "VARCHAR"),
                Map.entry("fromStatus", "VARCHAR"), Map.entry("toStatus", "VARCHAR"),
                Map.entry("reasonCode", "VARCHAR"), Map.entry("triggerType", "VARCHAR"),
                Map.entry("triggerId", "VARCHAR"), Map.entry("actorId", "BIGINT"),
                Map.entry("aggregateVersion", "INTEGER")
            );
        }

        private void assertSafeSql() {
            String normalized = normalized(raw);
            for (String forbidden : List.of("select *", "usegeneratedkeys", "auto_increment", " delete ",
                " drop ", " truncate ", " replace ", " on duplicate key ", " insert ignore ",
                " create table ", " alter table ", " foreign key ", " check (", "${")) {
                assertFalse(normalized.contains(forbidden), "禁止 SQL: " + forbidden);
            }
        }
    }
}

import com.paytech.risk.test.decision.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import tools.jackson.databind.json.JsonMapper;
import java.nio.file.*;
import java.util.*;
public class Cleanup {
 public static void main(String[] args) throws Exception {
  var json=JsonMapper.builder().build();var owned=json.readTree(Files.readString(Path.of("/tmp/rco19-stage5/owned.json")));
  var ds=new DriverManagerDataSource("jdbc:mysql://127.0.0.1:13306/mor_risk?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC","risk_app",System.getenv("MOR_RISK_DB_PASSWORD"));
  var jdbc=new JdbcTemplate(ds);var decision=new RiskDecisionTestMapper(jdbc);var flow=new RiskFlowTestMapper(jdbc);var variables=new RiskVariableTestMapper(jdbc);
  var before=decision.runtimeFactCounts(); if(before.values().stream().anyMatch(n->n!=0))throw new IllegalStateException("执行事实必须保持为空");
  var fixture=json.readTree(Files.readString(Path.of("/tmp/rco19-stage5/fixtures.json")));
  if(jdbc.queryForObject("SELECT COUNT(*) FROM t_rule_set_version WHERE rule_set_code=?",Long.class,fixture.path("rule").asString())!=2L)throw new IllegalStateException("规则版本数量已改变");
  var keys=owned.path("keys").valueStream().map(v->v.asString()).toList();
  // UI generated key is bound to this run's exact version and actor; it is not a generic cleanup prefix.
  int uiReceipt=jdbc.update("DELETE FROM t_command_idempotency WHERE caller_scope=? AND operation_type=? AND idempotency_key=? AND response_ref=?", "OPERATIONS_ACCOUNT:10001","CREATE_RULE_SET_VERSION","0dec0409-0174-4195-a0c0-a1d3d0f01a0f","rule-set-version:224751241454952448");
  for(var c:owned.path("flows"))flow.clean(c.asString());
  for(var c:owned.path("rules"))decision.deleteOwnedDecisionConfiguration(c.asString(),keys);
  variables.deleteOwned(owned.path("variables").valueStream().map(v->v.asString()).toList());
  Map<String,Object> result=new LinkedHashMap<>();result.put("uiReceiptDeleted",uiReceipt);result.put("runtimeFacts",decision.runtimeFactCounts());
  for(String table:List.of("t_risk_variable","t_risk_variable_member","t_rule_set_version","t_rule_operand","t_rule_action_parameter","t_decision_flow","t_decision_flow_edge"))result.put(table,jdbc.queryForObject("SELECT COUNT(*) FROM "+table,Long.class));
  result.put("ownedVariablesRemaining",jdbc.queryForObject("SELECT COUNT(*) FROM t_risk_variable WHERE variable_code LIKE 'AUTO!_VAR!_%' ESCAPE '!'",Long.class));
  result.put("seedThreshold",jdbc.queryForObject("SELECT number_value FROM t_risk_variable WHERE variable_code='DEMO_AMOUNT_THRESHOLD'",String.class));
  result.put("seedCurrency",jdbc.queryForObject("SELECT string_value FROM t_risk_variable WHERE variable_code='DEMO_STANDARD_CURRENCY'",String.class));
  if(!Long.valueOf(17).equals(result.get("t_risk_variable"))||!Long.valueOf(9).equals(result.get("t_risk_variable_member"))||!Long.valueOf(0).equals(result.get("ownedVariablesRemaining")))throw new IllegalStateException("初始化基线不一致");
  Files.writeString(Path.of("/tmp/rco19-stage5/cleanup.json"),json.writeValueAsString(result));System.out.println(json.writeValueAsString(result));
 }
}

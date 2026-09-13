import java.nio.file.Path;
import java.sql.DriverManager;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.logging.nologging.NoLoggingImpl;
import tools.jackson.databind.json.JsonMapper;
import com.paytech.risk.decision.core.dal.mapper.*;
import com.paytech.risk.decision.domain.authoring.*;
import com.paytech.risk.decision.domain.hash.RiskDecisionCanonicalizerImpl;
import com.paytech.risk.decision.domain.rule.RuleAstValidatorImpl;
import com.paytech.risk.decision.domainservice.port.DecisionIdentifierGenerator;
import com.paytech.risk.decision.domainservice.repository.impl.*;
import com.paytech.risk.decision.domainservice.service.impl.*;

/** Manual read-only migration evidence using actual packaged Reader and canonicalizer, no Spring startup. */
public class ReadRiskHistory {
    public static void main(String[] args) throws Exception {
        var mapper=JsonMapper.builder().findAndAddModules().build();
        var credentials=mapper.readTree(System.in.readAllBytes());
        var config=new Configuration();
        config.setLogImpl(NoLoggingImpl.class);
        for (var name:new String[]{"DecisionRuleSetMapper","DecisionReleaseMapper","DecisionDictionaryMapper"}) {
            var resource="mapper/decision/"+name+".xml";
            try(var stream=ReadRiskHistory.class.getClassLoader().getResourceAsStream(resource)) {
                if(stream==null)throw new IllegalStateException("Missing packaged mapper");
                new XMLMapperBuilder(stream,config,resource,config.getSqlFragments()).parse();
            }
        }
        try(var connection=DriverManager.getConnection("jdbc:mysql://127.0.0.1:23306/mor_risk?sslMode=DISABLED&allowPublicKeyRetrieval=true&connectionTimeZone=UTC", "root", credentials.get("password").asText())) {
            connection.setReadOnly(true);
            connection.setAutoCommit(false);
            try(var s=connection.createStatement()){s.execute("START TRANSACTION READ ONLY");}
            try(var session=new SqlSessionFactoryBuilder().build(config).openSession(connection)) {
                var ruleMapper=session.getMapper(DecisionRuleSetMapper.class);
                var repository=new DecisionRuleSetRepositoryImpl(ruleMapper);
                DecisionIdentifierGenerator noWrites=()->{throw new IllegalStateException("Migration reader must never allocate IDs");};
                var dictionary=new DecisionDictionaryServiceImpl(noWrites,new DecisionDictionaryRepositoryImpl(session.getMapper(DecisionDictionaryMapper.class)));
                var descriptor=CheckoutPrepareAuthoringDescriptor.v1();
                var actions=DecisionActionRegistry.checkoutPrepareV1();
                var canonicalizer=new RiskDecisionCanonicalizerImpl();
                var service=new DecisionRuleSetServiceImpl(repository,noWrites,new RuleAstValidatorImpl(descriptor,actions),canonicalizer,descriptor,actions,dictionary);
                var ids=new ArrayList<Long>();
                try(var s=connection.createStatement();var rs=s.executeQuery("SELECT rule_set_version_id FROM t_rule_set_version ORDER BY rule_set_version_id")){while(rs.next())ids.add(rs.getLong(1));}
                var rules=new ArrayList<Map<String,Object>>();
                for(var id:ids) {
                    // Existing deleted roots remain hidden in the management API. Read their already-persisted
                    // version IDs through the exact historical Reader used by the old replay path.
                    var reader=DecisionRuleSetServiceImpl.class.getDeclaredMethod("requireAggregateAfterKnownReference",long.class);
                    reader.setAccessible(true);
                    var value=(com.paytech.risk.decision.domain.rule.DecisionRuleSetAggregate)reader.invoke(service,id);
                    var ruleHash=canonicalizer.ruleSetVersion(value.ruleSetProjection()).sha256LowerHex();
                    var pointHash=canonicalizer.decisionPointVersion(value.pointVersionProjection()).sha256LowerHex();
                    if(!ruleHash.equals(value.version().contentHash())||!pointHash.equals(value.companion().contentHash()))throw new IllegalStateException("Stored history hash mismatch: "+id);
                    rules.add(Map.of("ruleSetVersionId",id.toString(),"ruleHash",ruleHash,"pointHash",pointHash,"inputFieldsHash",sha(mapper.writeValueAsBytes(value.inputFields()))));
                }
                var releaseRepository=new DecisionReleaseRepositoryImpl(session.getMapper(DecisionReleaseMapper.class),ruleMapper,repository);
                var constructor=DecisionReleaseServiceImpl.class.getConstructors()[0];
                var arguments=new ArrayList<Object>(java.util.List.of(releaseRepository,repository,noWrites,canonicalizer,descriptor,actions));
                if(constructor.getParameterCount()==7)arguments.add(service);
                var releaseService=(DecisionReleaseServiceImpl)constructor.newInstance(arguments.toArray());
                var releases=new ArrayList<Map<String,Object>>();
                try(var s=connection.createStatement();var rs=s.executeQuery("SELECT release_id,content_hash FROM t_decision_release ORDER BY release_id")) {
                    while(rs.next()) {
                        var id=rs.getLong(1);
                        releaseService.detail(id);
                        releaseService.replayCreate(id);
                        releases.add(Map.of("releaseId",Long.toString(id),"storedContentHash",rs.getString(2),"closureReadable",true,"releaseHashVerified",true,"createReplayReadable",true));
                    }
                }
                mapper.writeValue(Path.of(args[0]).toFile(),Map.of("scope","Actual packaged historical Reader in MySQL READ ONLY transaction; no business writes or full HTTP replay", "rules",rules,"releases",releases));
                connection.rollback();
                System.out.println("Historical rules="+rules.size()+", releases="+releases.size()+", all rule/point hashes match");
            }
        }
    }
    private static String sha(byte[] bytes) throws Exception {
        return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes));
    }
}

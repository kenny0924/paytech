import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import com.paytech.gateway.routing.snapshot.ApprovedTargetRegistry;
import com.paytech.gateway.routing.snapshot.ApprovedTargetRegistry.ApprovedTarget;
import com.paytech.gateway.routing.snapshot.GatewaySnapshotCompiler;
import com.paytech.gateway.routing.snapshot.InvalidGatewaySnapshotException;
import com.paytech.platform.support.api.gatewayconfiguration.snapshot.GatewayPublicSnapshot;
import tools.jackson.databind.json.JsonMapper;
/** 在本机内存验证待用户批准的一个路径根，不修改 Gateway 源码或运行配置。 */
public class CheckGatewayProposal {
    /** 保留已部署 Target 身份和旧根，只追加本轮字典 V1 路径，验证完整目录。 */
    public static void main(String[] args) {
        var mapper = JsonMapper.builder().findAndAddModules().build();
        var original = new ApprovedTargetRegistry();
        var target = original.requireRoutingTarget("operations-console-bff");
        var roots = new HashSet<>(target.downstreamPathRoots());
        if (!roots.add("/decision-dictionaries/v1/")) throw new AssertionError("unexpected preexisting approval");
        var augmented = new ApprovedTarget(target.targetServiceCode(),target.channel(),target.discoveryServiceName(),roots,target.routingUri());
        if (augmented.permitsDownstreamPath("/decision-dictionaries/v2/subject-tags") || augmented.permitsDownstreamPath("/decision-dictionaries-evil/v1/subject-tags")) throw new AssertionError("proposal overbroad");
        var compiler = new GatewaySnapshotCompiler(new ApprovedTargetRegistry(Set.of(augmented)));
        for (var arg : args) {
            var snapshot = mapper.readValue(Path.of(arg).toFile(), GatewayPublicSnapshot.class);
            System.out.println(compiler.compile(snapshot).catalog().all().size());
        }
        try {
            new GatewaySnapshotCompiler(original).compile(mapper.readValue(Path.of(args[args.length-1]).toFile(), GatewayPublicSnapshot.class));
            throw new AssertionError("deployed compiler unexpectedly accepts dictionaries");
        } catch (InvalidGatewaySnapshotException expected) {
            System.out.println("deployed-full-catalog-rejected; proposal-only-pass; source-unchanged");
        }
    }
}

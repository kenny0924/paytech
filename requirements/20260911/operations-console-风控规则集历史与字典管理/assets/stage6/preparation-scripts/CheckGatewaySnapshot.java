import java.nio.file.Path;
import com.paytech.gateway.routing.snapshot.ApprovedTargetRegistry;
import com.paytech.gateway.routing.snapshot.GatewaySnapshotCompiler;
import com.paytech.platform.support.api.gatewayconfiguration.snapshot.GatewayPublicSnapshot;
import tools.jackson.databind.json.JsonMapper;
/** 用当前 test 已部署 Gateway 的原类离线校验完整快照，不连接网络。 */
public class CheckGatewaySnapshot {
    /** 原始、维护、禁用注册及新增接口快照必须均可编译。 */
    public static void main(String[] args) {
        var mapper = JsonMapper.builder().findAndAddModules().build();
        var compiler = new GatewaySnapshotCompiler(new ApprovedTargetRegistry());
        for (var arg : args) {
            var snapshot = mapper.readValue(Path.of(arg).toFile(), GatewayPublicSnapshot.class);
            var compiled = compiler.compile(snapshot);
            System.out.println(compiled.catalog().all().size());
        }
    }
}

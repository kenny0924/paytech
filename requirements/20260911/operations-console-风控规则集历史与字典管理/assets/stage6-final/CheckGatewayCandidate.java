import java.nio.file.Path;
import com.paytech.gateway.routing.snapshot.GatewaySnapshotCompiler;
import com.paytech.platform.support.api.gatewayconfiguration.snapshot.GatewayPublicSnapshot;
import tools.jackson.databind.json.JsonMapper;

/** 用本轮候选 Gateway 的真实编译器离线验证部署环境的完整快照。 */
public class CheckGatewayCandidate {
    /** 只读取输入文件，不启动服务或连接数据库。 */
    public static void main(String[] args) {
        var mapper = JsonMapper.builder().findAndAddModules().build();
        var compiler = new GatewaySnapshotCompiler();
        for (var arg : args) {
            var snapshot = mapper.readValue(Path.of(arg).toFile(), GatewayPublicSnapshot.class);
            System.out.println(compiler.compile(snapshot).catalog().all().size());
        }
    }
}

import java.nio.file.*;
import com.paytech.gateway.configuration.PlatformSnapshotProperties;
import com.paytech.gateway.routing.snapshot.GatewaySnapshotCompiler;
import com.paytech.platform.support.api.gatewayconfiguration.response.GatewayPublicSnapshotResponseBody;
import tools.jackson.core.StreamReadConstraints;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.core.json.JsonFactory;
import tools.jackson.databind.json.JsonMapper;

/**
 * REQ-04 D-9：用候选 Gateway JAR 的生产代码检查一份公开 Snapshot 响应体——与 GatewaySnapshotConfiguration.snapshotObjectMapper 相同的
 * 严格解析（文档长度上限 = PlatformSnapshotProperties.MAX_RESPONSE_BYTES、嵌套深度、重复键检测），再用 GatewaySnapshotCompiler 编译。
 * WebClient 的 maxInMemorySize 取 max-response-bytes（与常量相同），这里以字节数对比常量代替。
 * 参数：<响应体 JSON 文件>。输出 DECODE+COMPILE OK / FAILED。
 */
public class GatewaySnapshotDecodeCheck {
    public static void main(String[] args) throws Exception {
        byte[] body = Files.readAllBytes(Path.of(args[0]));
        int cap = PlatformSnapshotProperties.MAX_RESPONSE_BYTES;
        if (body.length > cap) {
            System.out.println("FAILED: body " + body.length + " bytes > maxInMemorySize " + cap);
            System.exit(4);
        }
        StreamReadConstraints constraints = StreamReadConstraints.builder().maxDocumentLength(cap)
            .maxNestingDepth(PlatformSnapshotProperties.MAX_JSON_NESTING_DEPTH).build();
        JsonFactory factory = JsonFactory.builder().streamReadConstraints(constraints)
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build();
        GatewayPublicSnapshotResponseBody source = JsonMapper.builder(factory).build()
            .readValue(body, GatewayPublicSnapshotResponseBody.class);
        var snapshot = new GatewaySnapshotCompiler().compile(source);
        System.out.println("DECODE+COMPILE OK: body=" + body.length + " bytes, cap=" + cap + ", routes=" + snapshot.catalog().all().size()
            + ", targets=" + snapshot.targetCount());
    }
}

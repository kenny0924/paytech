import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Set;

/**
 * 币种主数据阶段 7 验收：在 ledger / billing 容器内读本机管理端口的 Prometheus 指标，只输出 Platform 币种规则查询的出站计数行。
 * <li>管理端口只绑 127.0.0.1，宿主机访问不到，所以由验收脚本编译后 docker cp 到容器 /tmp（tmpfs）再 docker exec 运行。</li>
 * <li>只允许 ledger 19105、billing 19113 两个固定地址；只读 GET，不改任何状态。</li>
 */
public final class CurrencySnapshotMetricsFetch {
    private static final Set<String> PORTS = Set.of("19105", "19113");

    private CurrencySnapshotMetricsFetch() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 1 || !PORTS.contains(args[0])) {
            System.err.println("usage: CurrencySnapshotMetricsFetch 19105|19113");
            System.exit(64);
        }
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + args[0] + "/actuator/prometheus"))
            .timeout(Duration.ofSeconds(5)).GET().build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            System.err.println("status " + response.statusCode());
            System.exit(1);
        }
        response.body().lines()
            .filter(line -> line.startsWith("http_client_requests_seconds_count") && line.contains("/platform/v1/currency-specs"))
            .forEach(System.out::println);
    }
}

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * 文件多存储并存阶段 7：在 Platform 容器的网络命名空间内调用内部文件接口（AC-09 无调用身份拒绝）
 * <li>由 fs7_common.internal_probe() 用宿主机 JDK 25 编译，经旁路容器（--network container:platform，只读挂载，--rm）运行。</li>
 * <li>mor-test 的 internal-auth.mode 为 OFF：没有任何调用身份，四个接口都应返回 401，带 X-Caller-Service 也一样。</li>
 * <li>只输出“场景 状态码”，不输出响应正文；不创建任何上传或文件。</li>
 */
public final class InternalFileProbe {

    private InternalFileProbe() {
    }

    public static void main(String[] args) throws Exception {
        String base = args[0] + "/internal/files/v1";
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
        String body = "{\"reasonCode\":\"FS7_PROBE\",\"filename\":\"probe.txt\",\"mimeType\":\"text/plain\",\"sizeBytes\":\"1\"}";
        print(client, "start-no-identity", post(base + "/uploads", body, null));
        print(client, "start-caller-header", post(base + "/uploads", body, "operations-console-bff"));
        print(client, "complete-no-identity", post(base + "/uploads/1/completion", null, null));
        print(client, "get-no-identity", HttpRequest.newBuilder(URI.create(base + "/files/1")).GET()
            .header("Accept", "application/json").timeout(Duration.ofSeconds(10)).build());
        print(client, "download-no-identity", post(base + "/files/1/download", null, null));
    }

    private static HttpRequest post(String url, String body, String caller) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url)).header("Accept", "application/json")
            .timeout(Duration.ofSeconds(10));
        if (caller != null) {
            builder.header("X-Caller-Service", caller);
        }
        if (body == null) {
            return builder.POST(HttpRequest.BodyPublishers.noBody()).build();
        }
        return builder.header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(body)).build();
    }

    private static void print(HttpClient client, String scenario, HttpRequest request) throws Exception {
        HttpResponse<Void> response = client.send(request, HttpResponse.BodyHandlers.discarding());
        System.out.println(scenario + " " + response.statusCode());
    }
}

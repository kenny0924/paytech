import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * 文件公开桶阶段 7：在接入 mor-test-backend 的一次性容器内调用 Platform 内部文件接口（Platform 无宿主机端口）
 * <li>与 V2 全链路脚本的 PrivateHttpProbe 同形：stdin 第一行方法，第二行完整 URL，随后每行一个 {@code Name: value} Header，空行后为请求体。</li>
 * <li>stdout 第一行 HTTP 状态码，其后为响应体；响应里的预签名地址只交给调用脚本，不打印到终端。</li>
 * <li>由 pb7_common.internal() 用宿主机 JDK 25 编译，容器以 --rm、只读挂载运行，不向运行中的容器写入任何文件。</li>
 */
public final class PrivateHttpProbe {

    private PrivateHttpProbe() {
    }

    public static void main(String[] args) throws IOException, InterruptedException {
        String[] parts = read(System.in).split("\n\n", 2);
        String[] lines = parts[0].split("\n");
        String body = parts.length > 1 ? parts[1] : "";
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(lines[1])).timeout(Duration.ofSeconds(30));
        for (int i = 2; i < lines.length; i++) {
            int colon = lines[i].indexOf(':');
            builder.header(lines[i].substring(0, colon).trim(), lines[i].substring(colon + 1).trim());
        }
        builder.method(lines[0], body.isEmpty() && !"POST".equals(lines[0])
            ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
        HttpResponse<String> response = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()
            .send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        System.out.println(response.statusCode());
        System.out.print(response.body());
    }

    private static String read(InputStream input) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        input.transferTo(buffer);
        return buffer.toString(StandardCharsets.UTF_8);
    }
}

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
 * mor-test 验收用的私网 HTTP 探针：在接入 mor-test-backend 的一次性容器内调用 Platform 私网接口。
 * <p>
 * stdin：第一行方法，第二行完整 URL，随后每行一个 {@code Name: value} Header，空行后为请求体。
 * stdout：第一行 HTTP 状态码，其后为响应体。请求经 stdin 传入，凭证不出现在命令行参数里。
 */
public final class PrivateHttpProbe {

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
        HttpResponse<String> response = HttpClient.newHttpClient().send(builder.build(),
            HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        System.out.println(response.statusCode());
        System.out.print(response.body());
    }

    private static String read(InputStream input) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        input.transferTo(buffer);
        return buffer.toString(StandardCharsets.UTF_8);
    }
}

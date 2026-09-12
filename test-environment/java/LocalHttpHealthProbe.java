import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Set;

/** 仅依赖 JRE 的本机 Java 服务就绪探针。 */
public final class LocalHttpHealthProbe {
    private static final int EXIT_FAILURE = 1;
    private static final int EXIT_USAGE = 64;
    private static final Set<String> READINESS_URLS = Set.of(
        "http://127.0.0.1:18104/actuator/health/readiness",
        "http://127.0.0.1:18106/platform-support/actuator/health/readiness",
        "http://127.0.0.1:19201/actuator/health/readiness",
        "http://127.0.0.1:19080/actuator/health/readiness"
    );

    @FunctionalInterface
    interface StatusTransport {
        int get(URI endpoint) throws IOException, InterruptedException;
    }

    private LocalHttpHealthProbe() {}

    public static void main(String[] args) {
        System.exit(run(args));
    }

    static int run(String[] args) {
        return run(args, LocalHttpHealthProbe::requestStatus);
    }

    static int run(String[] args, StatusTransport transport) {
        if (args == null || args.length != 1 || transport == null) {
            return EXIT_USAGE;
        }
        URI endpoint;
        try {
            endpoint = canonicalHttpUrl(args[0]);
        } catch (IllegalArgumentException error) {
            return EXIT_USAGE;
        }
        try {
            return isSuccessfulStatus(transport.get(endpoint)) ? 0 : EXIT_FAILURE;
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            return EXIT_FAILURE;
        } catch (IOException error) {
            return EXIT_FAILURE;
        }
    }

    static boolean isCanonicalHttpUrl(String value) {
        try {
            canonicalHttpUrl(value);
            return true;
        } catch (IllegalArgumentException error) {
            return false;
        }
    }

    static boolean isSuccessfulStatus(int status) {
        return status >= 200 && status < 300;
    }

    private static URI canonicalHttpUrl(String value) {
        if (value == null || !READINESS_URLS.contains(value)) {
            throw new IllegalArgumentException("A canonical HTTP readiness URL is required");
        }
        return URI.create(value);
    }

    private static int requestStatus(URI endpoint) throws IOException, InterruptedException {
        HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(2))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();
        HttpRequest request = HttpRequest.newBuilder(endpoint)
            .timeout(Duration.ofSeconds(3))
            .GET()
            .build();
        HttpResponse<Void> response = client.send(request, HttpResponse.BodyHandlers.discarding());
        return response.statusCode();
    }
}

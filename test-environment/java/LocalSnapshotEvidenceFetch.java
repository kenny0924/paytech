import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Set;

/** JRE-only body fetcher for the two fixed local Snapshot evidence endpoints. */
public final class LocalSnapshotEvidenceFetch {
    static final int MAX_BODY_BYTES = 1_048_576;

    private static final int EXIT_FAILURE = 1;
    private static final int EXIT_USAGE = 64;
    private static final Set<String> EVIDENCE_URLS = Set.of(
        "http://127.0.0.1:19080/actuator/prometheus",
        "http://platform-support-service:18106/platform-support/gateway-config/v1/public-snapshot"
    );

    @FunctionalInterface
    interface EvidenceTransport {
        EvidenceResponse get(URI endpoint) throws IOException, InterruptedException;
    }

    static final class EvidenceResponse {
        private final int status;
        private final byte[] body;

        EvidenceResponse(int status, byte[] body) {
            this.status = status;
            this.body = body;
        }

        int status() {
            return status;
        }

        byte[] body() {
            return body;
        }
    }

    private LocalSnapshotEvidenceFetch() {}

    public static void main(String[] args) {
        System.exit(run(args, LocalSnapshotEvidenceFetch::request, System.out));
    }

    static int run(String[] args, EvidenceTransport transport, OutputStream output) {
        if (args == null || args.length != 1 || transport == null || output == null) {
            return EXIT_USAGE;
        }
        URI endpoint;
        try {
            endpoint = canonicalEvidenceUrl(args[0]);
        } catch (IllegalArgumentException error) {
            return EXIT_USAGE;
        }

        try {
            EvidenceResponse response = transport.get(endpoint);
            if (response == null || response.body() == null) {
                return EXIT_FAILURE;
            }
            if (!isSuccessfulStatus(response.status())
                    || response.body().length > MAX_BODY_BYTES) {
                return EXIT_FAILURE;
            }
            output.write(response.body());
            output.flush();
            return 0;
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            return EXIT_FAILURE;
        } catch (IOException | RuntimeException error) {
            return EXIT_FAILURE;
        }
    }

    static boolean isCanonicalEvidenceUrl(String value) {
        try {
            canonicalEvidenceUrl(value);
            return true;
        } catch (IllegalArgumentException error) {
            return false;
        }
    }

    static boolean isSuccessfulStatus(int status) {
        return status >= 200 && status < 300;
    }

    private static URI canonicalEvidenceUrl(String value) {
        if (value == null || !EVIDENCE_URLS.contains(value)) {
            throw new IllegalArgumentException(
                "A canonical local Snapshot evidence URL is required"
            );
        }
        return URI.create(value);
    }

    private static EvidenceResponse request(URI endpoint)
            throws IOException, InterruptedException {
        HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(2))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();
        HttpRequest request = HttpRequest.newBuilder(endpoint)
            .timeout(Duration.ofSeconds(3))
            .GET()
            .build();
        HttpResponse<byte[]> response = client.send(
            request,
            HttpResponse.BodyHandlers.limiting(
                HttpResponse.BodyHandlers.ofByteArray(),
                MAX_BODY_BYTES
            )
        );
        return new EvidenceResponse(response.statusCode(), response.body());
    }
}

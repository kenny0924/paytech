import java.net.URI;
import java.net.http.*;
/** 只读 GET：打印状态码与响应体（前 4000 字符）。 */
public class HttpGet {
    public static void main(String[] a) throws Exception {
        var r = HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create(a[0])).GET().build(), HttpResponse.BodyHandlers.ofString());
        String b = r.body(); System.out.println(r.statusCode() + " " + (b.length() > 4000 ? b.substring(0, 4000) : b));
    }
}

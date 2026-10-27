import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.MediaType;
import java.nio.file.Files;
import java.nio.file.Path;
/** RestClient 默认 Jackson 转换器读取 JSON：args[0]=类名，args[1]=JSON 文件。 */
public class CompatCheck {
    public static void main(String[] a) throws Exception {
        var c = new JacksonJsonHttpMessageConverter();
        System.out.print("FAIL_ON_UNKNOWN_PROPERTIES=" + c.getMapper().isEnabled(tools.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES) + " ");
        byte[] body = Files.readAllBytes(Path.of(a[1]));
        HttpInputMessage msg = new HttpInputMessage() {
            public java.io.InputStream getBody() { return new java.io.ByteArrayInputStream(body); }
            public HttpHeaders getHeaders() { var h = new HttpHeaders(); h.setContentType(MediaType.APPLICATION_JSON); return h; }
        };
        try { Object o = c.read(Class.forName(a[0]), msg); System.out.println("READ OK"); }
        catch (Exception e) { System.out.println("READ FAILED " + e.getClass().getSimpleName() + ": " + String.valueOf(e.getMessage()).split("\n")[0]); }
    }
}

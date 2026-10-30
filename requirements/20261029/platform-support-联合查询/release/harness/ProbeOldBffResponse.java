import java.io.*;
import java.nio.charset.StandardCharsets;
import org.springframework.http.*;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
public class ProbeOldBffResponse {
    public static void main(String[] a) throws Exception {
        String json = "{\"depth\":1,\"nodes\":[],\"links\":[],\"discoveredKeys\":[],\"truncations\":[],\"alreadyLoaded\":[{\"templateCode\":\"X\",\"templateName\":\"x\",\"keyType\":\"ORDER_ID\",\"values\":[\"1\"]}],\"elapsedMillis\":3}";
        Class<?> type = Class.forName("com.paytech.platform.support.api.dataquery.response.JointQueryStepResponseBody");
        var conv = new JacksonJsonHttpMessageConverter();
        HttpInputMessage in = new HttpInputMessage() {
            public InputStream getBody() { return new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)); }
            public HttpHeaders getHeaders() { HttpHeaders h = new HttpHeaders(); h.setContentType(MediaType.APPLICATION_JSON); return h; }
        };
        try {
            Object o = conv.read((Class) type, in);
            System.out.println("OLD BFF api DTO accepts unknown alreadyLoaded: OK depth=" + type.getMethod("getDepth").invoke(o));
        } catch (Exception e) { System.out.println("OLD BFF rejects: " + e); }
    }
}

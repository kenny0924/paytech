import org.springframework.web.client.RestClient;
public class DefaultConverters {
    @SuppressWarnings("removal")
    public static void main(String[] a) {
        RestClient.builder().messageConverters(list -> list.forEach(c -> {
            String extra = "";
            if (c instanceof org.springframework.http.converter.json.JacksonJsonHttpMessageConverter j)
                extra = " FAIL_ON_UNKNOWN_PROPERTIES=" + j.getMapper().isEnabled(tools.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                    + " FAIL_ON_MISSING_CREATOR_PROPERTIES=" + j.getMapper().isEnabled(tools.jackson.databind.DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES);
            System.out.println(c.getClass().getSimpleName() + extra);
        }));
    }
}

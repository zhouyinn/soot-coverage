import org.junit.Test;

import java.util.Map;

import static org.junit.Assert.*;

public class LoggerTest {

    @Test
    public void testParseSimpleJsonToMap01() {
        String json = "{\"event\":\"SUBCONDITION_CHECKED\",\"file\":\"Hello.java\",\"line\":\"4\",\"index\":2}";
        Map<String, String> result = Logger.parseSimpleJsonToMap(json);
        assertEquals("SUBCONDITION_CHECKED", result.get("event"));
        assertEquals("Hello.java", result.get("file"));
        assertEquals("4", result.get("line"));
        assertEquals("2", result.get("index"));
        assertEquals(4, result.size()); // 4 keys expected
    }

    @Test
    public void testParseSimpleJsonToMap02() {
        String json = "{\"event\":\"SUBCONDITION_CHECKED\",\"file\":\"Hello.java\",\"line\":\"4\",\"index\":1}";
        Map<String, String> result = Logger.parseSimpleJsonToMap(json);
        assertEquals("SUBCONDITION_CHECKED", result.get("event"));
        assertEquals("Hello.java", result.get("file"));
        assertEquals("4", result.get("line"));
        assertEquals("1", result.get("index"));
        assertEquals(4, result.size()); // 4 keys expected
    }
}
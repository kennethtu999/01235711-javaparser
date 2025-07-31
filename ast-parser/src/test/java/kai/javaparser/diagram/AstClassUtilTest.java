package kai.javaparser.diagram;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.net.URISyntaxException;

import org.junit.jupiter.api.Test;

public class AstClassUtilTest {

    @Test
    void getSimpleClassName() throws IOException, URISyntaxException {
        String method = "com.b2c.mvc.B2CTaskPageCodeBase<pagecode.cac.CACQ001_1_Param,pagecode.cac.CACQ001_1_View>.getBundleString(java.lang.String)";
        String name = AstClassUtil.getSimpleClassName(method);
        
        assertEquals("B2CTaskPageCodeBase", name);
    }
}
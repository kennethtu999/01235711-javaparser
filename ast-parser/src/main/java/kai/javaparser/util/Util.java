package kai.javaparser.util;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

public class Util {
    private static final ObjectMapper objectMapper = new ObjectMapper();

    static {
        objectMapper.configure(SerializationFeature.INDENT_OUTPUT, true);
    }

    public static String toJson(Object object) {
        try {
            return objectMapper.writeValueAsString(object);
        } catch (JsonProcessingException e) {
            e.printStackTrace();
            return null;
        }
    }

    public static void writeJson(Path outputFile, Object object) throws IOException {
        try {
            objectMapper.writeValue(outputFile.toFile(), object);
        } catch (JsonProcessingException e) {
            e.printStackTrace();
        }
    }

    public static <T> T toObject(String json, Class<T> clazz) {
        try {
            return objectMapper.readValue(json, clazz);
        } catch (JsonProcessingException e) {
            e.printStackTrace();
            return null;
        }
    }

    public static <T> T readJson(Path cacheFilePath, Class<T> clazz) {
        try {
            return objectMapper.readValue(cacheFilePath.toFile(), clazz);
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }
}

package kai.javaparser.util;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 參數提取工具類
 * 提供從Map中提取各種類型參數的統一方法
 */
public class ParameterExtractor {

    private static final Logger logger = LoggerFactory.getLogger(ParameterExtractor.class);

    /**
     * 從參數Map中獲取字串參數
     */
    public static String getStringParam(Map<String, Object> params, String key, String defaultValue) {
        if (params == null) {
            return defaultValue;
        }
        Object value = params.get(key);
        return value != null ? value.toString() : defaultValue;
    }

    /**
     * 從參數Map中獲取整數參數
     */
    public static int getIntParam(Map<String, Object> params, String key, int defaultValue) {
        if (params == null) {
            return defaultValue;
        }
        Object value = params.get(key);
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        if (value instanceof String) {
            try {
                return Integer.parseInt((String) value);
            } catch (NumberFormatException e) {
                logger.warn("無法解析整數參數 {}: {}, 使用預設值: {}", key, value, defaultValue);
            }
        }
        return defaultValue;
    }

    /**
     * 從參數Map中獲取布林參數
     */
    public static boolean getBooleanParam(Map<String, Object> params, String key, boolean defaultValue) {
        if (params == null) {
            return defaultValue;
        }
        Object value = params.get(key);
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        if (value instanceof String) {
            return Boolean.parseBoolean((String) value);
        }
        return defaultValue;
    }

    /**
     * 從參數Map中獲取字串集合參數
     * 支援單一字串（逗號分隔）或字串陣列
     */
    public static Set<String> getStringSetParam(Map<String, Object> params, String key, Set<String> defaultValue) {
        if (params == null) {
            return defaultValue;
        }
        Object value = params.get(key);
        if (value == null) {
            return defaultValue;
        }

        Set<String> result = new HashSet<>();
        if (value instanceof String) {
            String strValue = (String) value;
            if (!strValue.trim().isEmpty()) {
                // 支援逗號分隔的字串
                String[] parts = strValue.split(",");
                for (String part : parts) {
                    String trimmed = part.trim();
                    if (!trimmed.isEmpty()) {
                        result.add(trimmed);
                    }
                }
            }
        } else if (value instanceof String[]) {
            // 支援字串陣列
            String[] arrayValue = (String[]) value;
            for (String item : arrayValue) {
                if (item != null && !item.trim().isEmpty()) {
                    result.add(item.trim());
                }
            }
        } else if (value instanceof java.util.Collection) {
            // 支援其他集合類型
            @SuppressWarnings("unchecked")
            java.util.Collection<Object> collection = (java.util.Collection<Object>) value;
            for (Object item : collection) {
                if (item != null) {
                    String strItem = item.toString().trim();
                    if (!strItem.isEmpty()) {
                        result.add(strItem);
                    }
                }
            }
        }

        return result.isEmpty() ? defaultValue : result;
    }
}

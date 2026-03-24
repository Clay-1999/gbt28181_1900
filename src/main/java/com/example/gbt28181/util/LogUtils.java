package com.example.gbt28181.util;

public final class LogUtils {

    private LogUtils() {}

    /** 将字符串中的换行符转义，使日志保持单行输出。 */
    public static String escape(Object obj) {
        if (obj == null) return "null";
        return obj.toString().replace("\r\n", "\\r\\n").replace("\n", "\\n").replace("\r", "\\r");
    }
}

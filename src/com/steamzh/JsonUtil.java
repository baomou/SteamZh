package com.steamzh;

import java.util.ArrayList;
import java.util.List;

/**
 * 极简 JSON 工具。
 * 只覆盖本项目用到的场景：取单个值、取某个 key 的所有值、转义字符串。
 */
public class JsonUtil {

    private static int skipWhitespace(String s, int i) {
        while (i < s.length() && Character.isWhitespace(s.charAt(i))) i++;
        return i;
    }

    /** 从 start 处的引号开始读一个 JSON 字符串，返回解码后的内容 */
    public static String readString(String json, int quoteIndex) {
        int i = quoteIndex + 1;
        StringBuilder sb = new StringBuilder();
        while (i < json.length()) {
            char c = json.charAt(i);
            if (c == '\\') {
                char next = json.charAt(i + 1);
                switch (next) {
                    case '"' -> sb.append('"');
                    case '\\' -> sb.append('\\');
                    case '/' -> sb.append('/');
                    case 'n' -> sb.append('\n');
                    case 'r' -> sb.append('\r');
                    case 't' -> sb.append('\t');
                    case 'b' -> sb.append('\b');
                    case 'f' -> sb.append('\f');
                    case 'u' -> {
                        String hex = json.substring(i + 2, i + 6);
                        sb.append((char) Integer.parseInt(hex, 16));
                        i += 4;
                    }
                    default -> sb.append(next);
                }
                i += 2;
            } else if (c == '"') {
                return sb.toString();
            } else {
                sb.append(c);
                i++;
            }
        }
        return null;
    }

    /** 取字符串值，例如 extractString(json, "value") */
    public static String extractString(String json, String key) {
        String needle = "\"" + key + "\"";
        int i = json.indexOf(needle);
        if (i < 0) return null;
        i += needle.length();
        i = skipWhitespace(json, i);
        if (i >= json.length() || json.charAt(i) != ':') return null;
        i = skipWhitespace(json, i + 1);
        if (i >= json.length() || json.charAt(i) != '"') return null;
        return readString(json, i);
    }

    /** 取出某个 key 的所有字符串值，按出现顺序 */
    public static List<String> extractAll(String json, String key) {
        List<String> out = new ArrayList<>();
        String needle = "\"" + key + "\"";
        int i = 0;
        while (true) {
            i = json.indexOf(needle, i);
            if (i < 0) break;
            i += needle.length();
            int j = skipWhitespace(json, i);
            if (j < json.length() && json.charAt(j) == ':') {
                j = skipWhitespace(json, j + 1);
                if (j < json.length() && json.charAt(j) == '"') {
                    String v = readString(json, j);
                    if (v != null) out.add(v);
                }
            }
        }
        return out;
    }

    /** 取数字值，例如 extractLong(json, "id") */
    public static Long extractLong(String json, String key) {
        String needle = "\"" + key + "\"";
        int i = json.indexOf(needle);
        if (i < 0) return null;
        i += needle.length();
        i = skipWhitespace(json, i);
        if (i >= json.length() || json.charAt(i) != ':') return null;
        i = skipWhitespace(json, i + 1);
        int start = i;
        while (i < json.length() && (Character.isDigit(json.charAt(i)) || json.charAt(i) == '-')) i++;
        if (start == i) return null;
        return Long.parseLong(json.substring(start, i));
    }

    /** 反转义一段 JSON 字符串内容（不带外层引号） */
    public static String unescape(String escaped) {
        return readString("\"" + escaped + "\"", 0);
    }

    /** 把普通字符串转成合法的 JSON 字符串字面量（带引号） */
    public static String escape(String s) {
        StringBuilder sb = new StringBuilder("\"");
        for (int k = 0; k < s.length(); k++) {
            char c = s.charAt(k);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                default -> {
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
                }
            }
        }
        return sb.append('"').toString();
    }
}

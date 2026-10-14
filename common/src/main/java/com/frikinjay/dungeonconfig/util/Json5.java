package com.frikinjay.dungeonconfig.util;

public final class Json5 {

    private Json5() {}

    public static String stripTrailingCommas(String text) {
        StringBuilder out = new StringBuilder(text.length());
        int n = text.length();
        int i = 0;
        while (i < n) {
            char c = text.charAt(i);
            int skip = commentEnd(text, i);
            if (skip > i) {
                out.append(text, i, skip);
                i = skip;
            } else if (c == '"' || c == '\'') {
                int end = i + 1;
                while (end < n && text.charAt(end) != c) end += text.charAt(end) == '\\' ? 2 : 1;
                end = Math.min(n, end + 1);
                out.append(text, i, end);
                i = end;
            } else {
                if (c == ',') {
                    int next = i + 1;
                    while (next < n) {
                        int after = commentEnd(text, next);
                        if (after > next) next = after;
                        else if (Character.isWhitespace(text.charAt(next))) next++;
                        else break;
                    }
                    if (next < n && (text.charAt(next) == '}' || text.charAt(next) == ']')) {
                        i++;
                        continue;
                    }
                }
                out.append(c);
                i++;
            }
        }
        return out.toString();
    }

    private static int commentEnd(String text, int i) {
        char c = text.charAt(i);
        boolean slash = c == '/' && i + 1 < text.length();
        if (c == '#' || (slash && text.charAt(i + 1) == '/')) {
            int end = text.indexOf('\n', i);
            return end < 0 ? text.length() : end;
        }
        if (slash && text.charAt(i + 1) == '*') {
            int end = text.indexOf("*/", i + 2);
            return end < 0 ? text.length() : end + 2;
        }
        return i;
    }
}

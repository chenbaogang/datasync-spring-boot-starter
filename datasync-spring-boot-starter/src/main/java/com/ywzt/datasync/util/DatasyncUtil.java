package com.ywzt.datasync.util;

import java.util.UUID;
import java.util.regex.Pattern;

public final class DatasyncUtil {

    private static final Pattern RULE = Pattern.compile("\\$\\{([^}]*)\\}");

    private DatasyncUtil() {
    }

    /** 32 位无横线 UUID */
    public static String uuId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    public static boolean isEmpty(String str) {
        return str == null || str.length() == 0;
    }

    public static boolean isNotEmpty(String str) {
        return !isEmpty(str);
    }

    /** 形如 ${table.field} 的取值规则 */
    public static boolean isRule(String var) {
        return isNotEmpty(var) && RULE.matcher(var).matches();
    }

    public static String substringBefore(String str, String separator) {
        if (isEmpty(str) || separator == null) {
            return str;
        }
        int pos = str.indexOf(separator);
        return pos < 0 ? str : str.substring(0, pos);
    }

    public static String substringBeforeLast(String str, String separator) {
        if (isEmpty(str) || isEmpty(separator)) {
            return str;
        }
        int pos = str.lastIndexOf(separator);
        return pos < 0 ? str : str.substring(0, pos);
    }

    public static String substringAfterLast(String str, String separator) {
        if (isEmpty(str)) {
            return str;
        }
        int pos = str.lastIndexOf(separator);
        return pos < 0 || pos == str.length() - separator.length() ? "" : str.substring(pos + separator.length());
    }

    public static String sha1Hex(String str) {
        if (isEmpty(str)) {
            return null;
        }
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA1");
            digest.update(str.getBytes("UTF-8"));
            byte[] bytes = digest.digest();
            StringBuilder sb = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16));
                sb.append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (Exception e) {
            return null;
        }
    }

    /** 剥离 SQL 末尾所有 order by 子句，返回查询主体 */
    public static String extractBeforeLastOrderBy(String sql) {
        Pattern pattern = Pattern.compile("^(.*?)(\\s+order\\s+by\\s+[^,]+)$", Pattern.CASE_INSENSITIVE);
        java.util.regex.Matcher matcher = pattern.matcher(sql);
        String result = sql;
        while (matcher.find()) {
            result = matcher.group(1).trim();
            matcher = pattern.matcher(result);
        }
        return result;
    }

    /** 取 SQL 最后一个 order by 的排序列部分 */
    public static String extractAfterLastOrderBy(String sql) {
        Pattern pattern = Pattern.compile("^(.*?)(\\s+order\\s+by\\s+)([^,]+)$", Pattern.CASE_INSENSITIVE);
        java.util.regex.Matcher matcher = pattern.matcher(sql);
        String result = "";
        while (matcher.find()) {
            result = matcher.group(3).trim();
            matcher = pattern.matcher(matcher.group(1).trim());
        }
        return result;
    }
}

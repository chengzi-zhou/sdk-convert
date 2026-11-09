package cheng.zhou.sdk.convert.provider.alipay;

import java.security.SecureRandom;
import java.util.Locale;

/**
 * 订单串 → 支付宝 iOS 原生 SafePay 深链：
 * alipay://alipayclient/?{"fromAppUrlScheme":"...","requestType":"SafePay","extra":"...","dataString":"..."}
 */
public final class IosSafePayLink {

    private static final String SCHEME = "alipay://alipayclient/?";

    private static final SecureRandom RND = new SecureRandom();

    private IosSafePayLink() {
    }

    public static String build(String orderStr, SafePayContext context) {
        if (orderStr == null || orderStr.isEmpty()) {
            return "";
        }
        String token = newToken();
        String dataString = orderStr + "&bizcontext=" + context.bizContext(token);
        String extra = context.extra(token, today(), today());
        String json = "{\"fromAppUrlScheme\":\"" + jsonEscape(context.fromAppUrlScheme()) + "\","
                + "\"requestType\":\"SafePay\","
                + "\"extra\":\"" + jsonEscape(extra) + "\","
                + "\"dataString\":\"" + jsonEscape(dataString) + "\"}";
        return SCHEME + pctEncode(json);
    }

    /** EP + 32 位小写十六进制 + _ + 毫秒时间戳（与 App 生成的 token 同构）。 */
    private static String newToken() {
        StringBuilder sb = new StringBuilder("EP");
        for (int i = 0; i < 32; i++) {
            sb.append(Character.forDigit(RND.nextInt(16), 16));
        }
        return sb.append('_').append(today()).toString();
    }

    private static long today() {
        return System.currentTimeMillis();
    }

    static String pctEncode(String s) {
        if (s == null) {
            return "";
        }
        byte[] bytes = s.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            int c = b & 0xFF;
            if ((c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')
                    || c == '-' || c == '_' || c == '.' || c == '~') {
                sb.append((char) c);
            } else {
                sb.append('%')
                        .append(Character.toUpperCase(Character.forDigit((c >> 4) & 0xF, 16)))
                        .append(Character.toUpperCase(Character.forDigit(c & 0xF, 16)));
            }
        }
        return sb.toString();
    }

    static String jsonEscape(String s) {
        StringBuilder sb = new StringBuilder(s.length() + 16);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"':
                    sb.append("\\\"");
                    break;
                case '\\':
                    sb.append("\\\\");
                    break;
                case '\n':
                    sb.append("\\n");
                    break;
                case '\r':
                    sb.append("\\r");
                    break;
                case '\t':
                    sb.append("\\t");
                    break;
                default:
                    if (c < 0x20) {
                        sb.append(String.format(Locale.ROOT, "\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                    break;
            }
        }
        return sb.toString();
    }
}

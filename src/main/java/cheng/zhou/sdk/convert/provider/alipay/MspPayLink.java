package cheng.zhou.sdk.convert.provider.alipay;

import lombok.extern.slf4j.Slf4j;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * 支付宝 MSP 收银台会话（alipay.msp.cashier.dispatch.bytes）
 */
@Slf4j
public final class MspPayLink {

    /** 网关地址。 */
    private static final String MGW_URL = "https://mobilegw.alipay.com/mgw.htm";

    /** 代理地址。 */
    private static final String PROXY_URL = "http://122.246.4.30:17981";

    /** H5 继续支付地址（服务商用 h5_route_token 直接进入收银台，浏览器打开即显示订单）。 */
    private static final String H5_CONTINUE_URL = "https://mclient.alipay.com/h5Continue.htm";

    /** 支付宝 SDK 内置 RSA 公钥（SDK 常量 com.alipay.sdk.m.n.a.g）。 */
    private static final String RSA_PUBLIC_KEY =
            "MIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQDENksAVqDoz5SMCZq0bsZwE+I3NjrANyTTwUVSf1+"
                    + "ec1PfPB4tiocEpYJFCYju9MIbawR8ivECbUWjpffZq5QllJg+19CB7V5rYGcEnb/M7CS3lFF2sNcRFJUtXUUAqyR3"
                    + "/l7PmpxTwObZ4DLG258dhE2vFlVGXjnuLs+FI2hg4QIDAQAB";

    private static final Pattern P_SESSION = Pattern.compile("\"session\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern P_CONTROL = Pattern.compile("\"control_type\"\\s*:\\s*\"([^\"]*)\"");
    private static final SecureRandom RND = new SecureRandom();

    private static byte[] postEnvelope(String orderStr, byte[] envelope) {
        try {
            //避免风控检测，此处改轮询多个代理ip
            URI proxyUri = URI.create(PROXY_URL);
            Proxy proxy = new Proxy(Proxy.Type.HTTP,
                    InetSocketAddress.createUnresolved(proxyUri.getHost(), proxyUri.getPort()));
            return post(orderStr, envelope, proxy);
        } catch (Exception e) {
            log.error("proxy {} unavailable: {}", PROXY_URL, e.toString());
            throw new RuntimeException("代理出口异常", e);
        }
    }

    private static byte[] post(String orderStr, byte[] envelope, Proxy proxy) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) URI.create(MGW_URL).toURL().openConnection(proxy);
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(30000);
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setRequestProperty("User-Agent", "msp");
        conn.setRequestProperty("Accept-Charset", "UTF-8");
        conn.setRequestProperty("AppId", "TAOBAO");
        conn.setRequestProperty("Version", "2.0");
        conn.setRequestProperty("Content-Type", "application/octet-stream");
        conn.setRequestProperty("msp-gzip", "true");
        conn.setRequestProperty("des-mode", "CBC");
        conn.setRequestProperty("Operation-Type", "alipay.msp.cashier.dispatch.bytes");
        conn.setRequestProperty("Msp-Param", mspParam(orderStr));
        conn.setRequestProperty("Accept-Encoding", "gzip");

        try (OutputStream os = conn.getOutputStream()) {
            os.write(envelope);
        }

        int code = conn.getResponseCode();
        InputStream in = code >= 400 ? conn.getErrorStream() : conn.getInputStream();
        if (in == null) {
            throw new IllegalStateException("mgw HTTP " + code + " no body");
        }

        byte[] raw;
        try (InputStream is = in) {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] data = new byte[8192];
            int nRead;
            while ((nRead = is.read(data, 0, data.length)) != -1) {
                buffer.write(data, 0, nRead);
            }
            buffer.flush();
            raw = buffer.toByteArray();
        } finally {
            conn.disconnect();
        }

        if (code / 100 != 2) {
            throw new IllegalStateException("mgw HTTP " + code + " body="
                    + new String(raw, 0, Math.min(200, raw.length), StandardCharsets.UTF_8));
        }
        return raw;
    }

    private MspPayLink() {
    }

    /** dispatch 结果。 */
    public static final class Result {
        private final String session;
        private final String controlType;
        private final String invokeAlipayData;

        public Result(String session, String controlType, String invokeAlipayData) {
            this.session = session;
            this.controlType = controlType;
            this.invokeAlipayData = invokeAlipayData;
        }

        public String session() { return session; }
        public String controlType() { return controlType; }
        public String invokeAlipayData() { return invokeAlipayData; }
    }

    // =====================================================================
    // 对外主入口
    // =====================================================================

    /**
     * 用 orderStr（alipay.trade.app.pay 的 SDK 下单串）+ 设备档案创建收银台会话。
     *
     * @param orderStr 支付宝 SDK order 串
     * @param profile  设备身份档案（推荐 {@link DeviceProfile#random()}；
     *                 payload 的 user_agent/new_client_key/pa 按 SDK 源码精确构造，
     *                 服务端会从 UA 解析 client_key/vimei/vimsi 与 tid 交叉校验）
     */
    public static Result dispatch(String orderStr, DeviceProfile profile) throws Exception {
        if (orderStr == null || orderStr.isEmpty()) {
            throw new IllegalArgumentException("orderStr 为空");
        }
        String dev = (profile.deviceModel == null || profile.deviceModel.isEmpty())
                ? "21091116AC" : profile.deviceModel;
        String meta = "{\"data\":{\"api_name\":\"com.alipay.mcpay\","
                + "\"namespace\":\"com.alipay.mobilecashier\",\"api_version\":\"4.9.0\","
                + "\"device\":\"" + jsonEscape(dev) + "\",\"params\":{}}}";

        String payload = "{\"action\":{\"type\":\"cashier\",\"method\":\"main\"},"
                + "\"external_info\":" + jsonString(orderStr) + ","
                + "\"tid\":" + jsonString(nullToEmpty(profile.tid)) + ","
                + "\"user_agent\":" + jsonString(profile.userAgent()) + ","
                + "\"has_alipay\":true,\"has_msp_app\":true,"
                + "\"app_key\":\"2014052600006128\","
                + "\"utdid\":" + jsonString(nullToEmpty(profile.utdid)) + ","
                + "\"new_client_key\":" + jsonString(nullToEmpty(profile.clientKey)) + ","
                + "\"pa\":" + jsonString(profile.pa()) + "}";

        byte[] gzBody = gzip(payload.getBytes(StandardCharsets.UTF_8));
        byte[] key24 = randomKey24();
        byte[] encBody = desedeCbc(key24, gzBody, Cipher.ENCRYPT_MODE);
        byte[] encKey = rsaEncrypt(key24);

        ByteArrayOutputStream env = new ByteArrayOutputStream();
        writeSegment(env, meta.getBytes(StandardCharsets.UTF_8));
        writeSegment(env, encKey);
        writeSegment(env, encBody);

        byte[] raw = postEnvelope(orderStr, env.toByteArray());
        if (isGzip(raw)) {
            raw = gunzip(raw);
        }
        if (raw.length < 10 || !isDigit(raw[0]) || !isDigit(raw[4])) {
            throw new IllegalStateException("mgw 响应格式异常: "
                    + new String(raw, StandardCharsets.UTF_8).substring(0, Math.min(200, raw.length)));
        }

        // [meta][cipher]
        int off = 0;
        int metaLen = lenAt(raw, off);
        off += 5 + metaLen;                       // 跳过响应 meta
        int cipherLen = lenAt(raw, off);
        off += 5;
        byte[] cipher = new byte[cipherLen];
        System.arraycopy(raw, off, cipher, 0, cipherLen);

        byte[] plain = desedeCbc(key24, cipher, Cipher.DECRYPT_MODE);
        plain = stripPadding(plain);
        if (isGzip(plain)) {
            plain = gunzip(plain);
        }
        String body = new String(plain, StandardCharsets.UTF_8);

        String session = firstGroup(P_SESSION, body);
        if (session == null || session.isEmpty()) {
            throw new IllegalStateException("dispatch 未返回 session: " + body);
        }
        String control = firstGroup(P_CONTROL, body);
        String invoke = extractJsonObject(body, "invokeAlipayData");
        return new Result(session, control == null ? "" : control, invoke);
    }

    /** 取出 body 里某个 JSON 对象字段的内容（不含外层大括号，保持原始转义）。 */
    static String extractJsonObject(String body, String key) {
        int keyIdx = body.indexOf("\"" + key + "\"");
        if (keyIdx < 0) {
            return "";
        }
        int start = body.indexOf('{', keyIdx);
        if (start < 0) {
            return "";
        }
        int depth = 0;
        boolean inStr = false;
        boolean esc = false;
        for (int i = start; i < body.length(); i++) {
            char c = body.charAt(i);
            if (inStr) {
                if (esc) {
                    esc = false;
                } else if (c == '\\') {
                    esc = true;
                } else if (c == '"') {
                    inStr = false;
                }
            } else if (c == '"') {
                inStr = true;
            } else if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0) {
                    return body.substring(start + 1, i);
                }
            }
        }
        return "";
    }

    /** 浏览器打开的 H5 继续支付链接（对齐聚合页 android_url 的写法）。 */
    public static String h5ContinueLink(String session) {
        return H5_CONTINUE_URL + "?h5_route_token=" + urlEncode(nullToEmpty(session));
    }

    /**
     * 复刻 SDK 的 Msp-Param：从 orderStr 里找含 trade_no 的段，取其值。
     * （orderStr 的 biz_content 段里带 "out_trade_no"，SDK 用 contains("trade_no") 命中。）
     */
    private static String mspParam(String orderStr) {
        if (orderStr == null || orderStr.isEmpty()) {
            return "";
        }
        for (String seg : orderStr.split("&")) {
            if (seg.contains("trade_no") && !seg.startsWith("out_trade_no")) {
                int eq = seg.indexOf('=');
                if (eq > 0 && eq < seg.length() - 1) {
                    return "trade_no=" + seg.substring(eq + 1).replace("\"", "");
                }
            }
        }
        return "";
    }

    // =====================================================================
    // 加密
    // =====================================================================

    private static byte[] desedeCbc(byte[] key, byte[] data, int mode) throws Exception {
        Cipher cipher = Cipher.getInstance("DESede/CBC/PKCS5Padding");
        cipher.init(mode, new SecretKeySpec(key, "DESede"), new IvParameterSpec(new byte[8]));
        return cipher.doFinal(data);
    }

    private static byte[] rsaEncrypt(byte[] data) throws Exception {
        byte[] der = Base64.getDecoder().decode(RSA_PUBLIC_KEY);
        PublicKey pub = KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(der));
        Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
        cipher.init(Cipher.ENCRYPT_MODE, pub);
        return cipher.doFinal(data);
    }

    /** 24 位 [A-Za-z0-9] 随机串，对应 SDK 的 q.a(24)。 */
    private static byte[] randomKey24() {
        final String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
        byte[] out = new byte[24];
        for (int i = 0; i < out.length; i++) {
            out[i] = (byte) chars.charAt(RND.nextInt(chars.length()));
        }
        return out;
    }

    // =====================================================================
    // 信封工具
    // =====================================================================

    private static void writeSegment(ByteArrayOutputStream out, byte[] data) throws IOException {
        out.write(String.format(Locale.ROOT, "%05d", data.length).getBytes(StandardCharsets.US_ASCII));
        out.write(data);
    }

    private static int lenAt(byte[] buf, int off) {
        return Integer.parseInt(new String(buf, off, 5, StandardCharsets.US_ASCII));
    }

    private static boolean isDigit(byte b) {
        return b >= '0' && b <= '9';
    }

    private static boolean isGzip(byte[] b) {
        return b != null && b.length > 2 && (b[0] & 0xFF) == 0x1F && (b[1] & 0xFF) == 0x8B;
    }

    private static byte[] gzip(byte[] data) throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (GZIPOutputStream gz = new GZIPOutputStream(bos)) {
            gz.write(data);
        }
        return bos.toByteArray();
    }

    private static byte[] gunzip(byte[] data) throws Exception {
        try (GZIPInputStream gz = new GZIPInputStream(new ByteArrayInputStream(data))) {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = gz.read(buf)) > 0) {
                bos.write(buf, 0, n);
            }
            return bos.toByteArray();
        }
    }

    /** PKCS5 解填充（DESede 会自行处理，这里兜底）。 */
    private static byte[] stripPadding(byte[] data) {
        if (data == null || data.length == 0) {
            return new byte[0];
        }
        int pad = data[data.length - 1] & 0xFF;
        if (pad >= 1 && pad <= 8 && pad <= data.length) {
            byte[] out = new byte[data.length - pad];
            System.arraycopy(data, 0, out, 0, data.length - pad);
            return out;
        }
        return data;
    }

    // =====================================================================
    // JSON 小工具（零外部依赖）
    // =====================================================================

    static String jsonString(String s) {
        return "\"" + jsonEscape(s == null ? "" : s) + "\"";
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

    private static String firstGroup(Pattern p, String text) {
        Matcher m = p.matcher(text);
        return m.find() ? m.group(1) : null;
    }

    private static String urlEncode(String s) {
        try {
            return java.net.URLEncoder.encode(s == null ? "" : s, "UTF-8");
        } catch (java.io.UnsupportedEncodingException e) {
            throw new RuntimeException("UTF-8 encoding not supported", e);
        }
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }
}
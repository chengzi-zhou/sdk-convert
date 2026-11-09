package cheng.zhou.sdk.convert.provider.alipay;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Locale;

/**
 * 阿里 UTDID / tid 设备指纹生成器
 */
public final class DeviceFp {

    private static final String HMAC_KEY = "d6fc3a4a06adbde89223bvefedc24fecde188aaa9161";
    private static final SecureRandom RND = new SecureRandom();

    private DeviceFp() {
    }

    /** 生成一个新的 24 字符 UTDID。 */
    public static String genUtdid() {
        return genUtdid(randomImei());
    }

    /** 用指定 IMEI 生成 UTDID（同设备重复调用产生相同 hash 段）。 */
    public static String genUtdid(String imei) {
        byte[] buf = new byte[18];
        int ts = (int) (System.currentTimeMillis() / 1000L);
        putInt(buf, 0, ts);
        putInt(buf, 4, RND.nextInt());
        buf[8] = 3;    // SDK 版本常量
        buf[9] = 0;
        putInt(buf, 10, javaHashCode(imei));
        byte[] head = new byte[14];
        System.arraycopy(buf, 0, head, 0, 14);
        putInt(buf, 14, javaHashCode(hmacSha1B64(HMAC_KEY, head)));
        return Base64.getEncoder().encodeToString(buf);
    }

    /** 生成 64 位 hex 随机 tid。 */
    public static String genTid() {
        byte[] b = new byte[32];
        RND.nextBytes(b);
        StringBuilder sb = new StringBuilder(64);
        for (byte x : b) {
            sb.append(String.format(Locale.ROOT, "%02x", x));
        }
        return sb.toString();
    }

    /** 随机 15 位 IMEI 形态字符串。 */
    public static String randomImei() {
        StringBuilder sb = new StringBuilder(15);
        sb.append((char) ('1' + RND.nextInt(9)));
        for (int i = 1; i < 15; i++) {
            sb.append((char) ('0' + RND.nextInt(10)));
        }
        return sb.toString();
    }

    /** java.lang.String.hashCode（含符号回绕，与 SDK 的 StringUtils.hashCode 一致）。 */
    public static int javaHashCode(String s) {
        int h = 0;
        for (int i = 0; i < s.length(); i++) {
            h = 31 * h + s.charAt(i);
        }
        return h;
    }

    private static String hmacSha1B64(String key, byte[] data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA1"));
            return Base64.getEncoder().encodeToString(mac.doFinal(data));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static void putInt(byte[] buf, int off, int v) {
        buf[off] = (byte) (v >>> 24);
        buf[off + 1] = (byte) (v >>> 16);
        buf[off + 2] = (byte) (v >>> 8);
        buf[off + 3] = (byte) v;
    }
}

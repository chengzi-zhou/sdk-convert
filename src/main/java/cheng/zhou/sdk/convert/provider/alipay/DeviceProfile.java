package cheng.zhou.sdk.convert.provider.alipay;

import java.util.concurrent.ThreadLocalRandom;

/**
 * 支付宝 SDK 设备身份档案
 */
public final class DeviceProfile {

    /** 服务端签发的设备指纹 tid（64 hex）。 */
    public String tid;
    /** 客户端密钥（首init = 时间戳hex后10位）。 */
    public String clientKey;
    /** UTDID（24 字符）。 */
    public String utdid;
    /** UA 内嵌虚拟 IMEI。 */
    public String vimei;
    /** UA 内嵌虚拟 IMSI。 */
    public String vimsi;
    /** 内核版本（UA 用）。 */
    public String kernel = "4.14.186-perf+";
    /** Build.MODEL。 */
    public String deviceModel = "21091116AC";
    /** 品牌。 */
    public String brand = "Xiaomi";
    /** 屏幕分辨率。 */
    public String screen = "1080*2260";
    /** 上报用的 apdidToken（真机档案才有；虚拟身份随机生成）。 */
    public String apdidToken = "";

    public static DeviceProfile random() {
        DeviceProfile p = new DeviceProfile();
        p.utdid = DeviceFp.genUtdid();
        p.tid = DeviceFp.genTid();
        // SDK 首init形态：client_key = 时间戳hex后10位；vimei/vimsi = hex ts + 4位随机
        String tsHex = Long.toHexString(System.currentTimeMillis());
        if (tsHex.length() > 10) {
            tsHex = tsHex.substring(tsHex.length() - 10);
        }
        p.clientKey = tsHex;
        String base = tsHex.startsWith("1") ? tsHex : "1" + tsHex;
        p.vimsi = base + randDigits(4);
        p.vimei = base + randDigits(4);
        p.apdidToken = randomApdidToken();
        return p;
    }

    /**
     * 按 SDK com.alipay.sdk.m.o.c.a(aVar, aVar2, z) 精确重建 payload user_agent。
     * 字段顺序与分隔符不能改动——服务端会从中解析 client_key/vimei/vimsi 与 tid 交叉校验。
     */
    public String userAgent() {
        return "Msp/15.8.42 ("
                + "Android 11;Linux " + kernel + ";"
                + "zh_CN;-1;-1;"
                + screen + ";"
                + "15.0;"
                + "000000000000000;-1;-1;1;000000000000000;000000000000000;"
                + n(clientKey) + ";"
                + brand + ";" + deviceModel + ";"
                + "false;"
                + "00:00:00:00:00:00;-1;-1;"
                + "sdk-and-lite;"
                + n(vimsi) + ";" + n(vimei) + ";"
                + "-1;00)";
    }

    /** dispatch payload 的 pa 字段：(包名;版本号)。 */
    public String pa() {
        return "(com.mg.ec;596)";
    }

    private static String randDigits(int n) {
        StringBuilder sb = new StringBuilder(n);
        for (int i = 0; i < n; i++) {
            sb.append((char) ('0' + ThreadLocalRandom.current().nextInt(10)));
        }
        return sb.toString();
    }

    /** 仿 SDK apdidToken 形态的 56 字符 base64 串。 */
    private static String randomApdidToken() {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
        StringBuilder sb = new StringBuilder(48);
        ThreadLocalRandom r = ThreadLocalRandom.current();
        for (int i = 0; i < 48; i++) {
            sb.append(chars.charAt(r.nextInt(chars.length())));
        }
        sb.append("QEAAA==");
        return sb.toString();
    }

    private static String n(String s) {
        return s == null ? "" : s;
    }
}

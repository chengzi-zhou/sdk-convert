package cheng.zhou.sdk.convert.provider.alipay;

/**
 * iOS SafePay 的 App 配置：每个 App 一套 app_name/sdk_ver/an/av/sv。
 */
public final class SafePayContext {

    /** 支付宝 SDK 固定 appkey。 */
    private static final String APP_KEY = "2014052600006128";

    private final String fromAppUrlScheme;
    private final String appName;
    private final String sdkVer;
    private final String an;
    private final String av;
    private final String sv;

    public SafePayContext(String fromAppUrlScheme, String appName, String sdkVer,
                          String an, String av, String sv) {
        this.fromAppUrlScheme = fromAppUrlScheme;
        this.appName = appName;
        this.sdkVer = sdkVer;
        this.an = an;
        this.av = av;
        this.sv = sv;
    }

    public String fromAppUrlScheme() {
        return fromAppUrlScheme;
    }

    /** bizcontext（不带 digest），ap_link_token 必须与 extra.token 一致。 */
    public String bizContext(String token) {
        return "{\"appkey\":\"" + APP_KEY + "\",\"an\":\"" + an + "\",\"av\":\"" + av
                + "\",\"lc\":\"u\",\"ty\":\"ios_lite\",\"sv\":\"" + sv
                + "\",\"extInfo\":{\"ap_link_token\":\"" + token + "\"}}";
    }

    /** extra。 */
    public String extra(String token, long tsApiInvoke, long tsCallsp) {
        return "{\"call_type\":\"pay\",\"app_name\":\"" + appName + "\","
                + "\"ts_callsp_type\":\"ulink\",\"token\":\"" + token + "\","
                + "\"ts_api_invoke\":\"" + tsApiInvoke + "\",\"sdk_ver\":\"" + sdkVer + "\","
                + "\"ts_callsp\":\"" + tsCallsp + "\"}";
    }
}

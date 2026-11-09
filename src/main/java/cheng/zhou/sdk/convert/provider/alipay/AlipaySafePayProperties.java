package cheng.zhou.sdk.convert.provider.alipay;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * iOS SafePay 的 App 配置：按订单 app_id 取对应 App 的 an/av/sv 等。
 */
@Data
@Component
@ConfigurationProperties(prefix = "alipay.safe-pay")
public class AlipaySafePayProperties {

    /** app_id → App 身份。 */
    private Map<String, SafePayApp> apps = new LinkedHashMap<String, SafePayApp>();

    public SafePayContext resolve(String appId) {
        SafePayApp app = appId == null ? null : apps.get(appId);
        if (app == null) {
            throw new IllegalArgumentException("未配置 app_id=" + appId + " 的 iOS SafePay 身份");
        }
        return app.toContext(appId);
    }

    @Data
    public static class SafePayApp {

        private String fromAppUrlScheme;
        private String appName;
        private String sdkVer;
        private String an;
        private String av;
        private String sv;

        SafePayContext toContext(String appId) {
            require(appId, "from-app-url-scheme", fromAppUrlScheme);
            require(appId, "app-name", appName);
            require(appId, "sdk-ver", sdkVer);
            require(appId, "an", an);
            require(appId, "av", av);
            require(appId, "sv", sv);
            return new SafePayContext(fromAppUrlScheme, appName, sdkVer, an, av, sv);
        }

        private static void require(String appId, String name, String value) {
            if (value == null || value.trim().isEmpty()) {
                throw new IllegalArgumentException("app_id=" + appId + " 的 SafePay 配置缺少 " + name);
            }
        }
    }
}

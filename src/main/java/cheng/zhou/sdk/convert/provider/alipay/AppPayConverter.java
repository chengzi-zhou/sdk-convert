package cheng.zhou.sdk.convert.provider.alipay;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.net.URLDecoder;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * APP 支付订单串 → h5Continue 收银台链接 / iOS SafePay 链接。
 */
@Slf4j
public final class AppPayConverter {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final DateTimeFormatter EXPIRE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private AppPayConverter() {
    }

    /** h5_link：dispatch 换 session，拼 h5Continue 链接。 */
    public static String buildH5Url(String orderStr) throws Exception {
        return MspPayLink.h5ContinueLink(dispatch(orderStr).session());
    }

    /** ios_app_pay：dispatch 成功后按 App 身份生成 SafePay 深链。 */
    public static String buildIosUrl(String orderStr, SafePayContext context) throws Exception {
        dispatch(orderStr);
        return IosSafePayLink.build(orderStr, context);
    }

    private static MspPayLink.Result dispatch(String orderStr) throws Exception {
        if (orderStr == null || orderStr.isEmpty()) {
            throw new IllegalArgumentException("orderStr 为空");
        }
        if (!isAppPay(orderStr)) {
            throw new IllegalArgumentException("仅支持 APP 支付订单串（alipay.trade.app.pay）转换");
        }
        checkExpired(orderStr);

        MspPayLink.Result r = MspPayLink.dispatch(orderStr, DeviceProfile.random());
        log.info("dispatch ok control={} session={} invoke={}", r.controlType(), r.session(),
                r.invokeAlipayData().isEmpty() ? "no" : "yes");
        if (r.invokeAlipayData().isEmpty()) {
            log.warn("dispatch response missing invokeAlipayData, session={}", r.session());
            throw new IllegalStateException("支付宝收银台会话获取失败，请稍后重试");
        }
        return r;
    }

    private static boolean isAppPay(String orderStr) {
        return "alipay.trade.app.pay".equals(paramValue(orderStr, "method"));
    }

    private static String paramValue(String content, String key) {
        for (String pair : content.split("&")) {
            int eq = pair.indexOf('=');
            if (eq >= 0 && key.equals(pair.substring(0, eq).trim())) {
                return pair.substring(eq + 1);
            }
        }
        return null;
    }

    /** 订单的 time_expire 已过时直接拦下，避免支付宝报「商家订单参数异常」。 */
    private static void checkExpired(String orderStr) {
        String biz = paramValue(orderStr, "biz_content");
        if (biz == null || biz.isEmpty()) {
            return;
        }
        try {
            JsonNode node = MAPPER.readTree(URLDecoder.decode(biz, "UTF-8")).get("time_expire");
            if (node == null || !node.isTextual()) {
                return;
            }
            if (LocalDateTime.parse(node.asText(), EXPIRE_FORMAT).isBefore(LocalDateTime.now())) {
                throw new IllegalArgumentException("订单已过期（time_expire=" + node.asText() + "），请重新下单");
            }
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception ignored) {
        }
    }
}

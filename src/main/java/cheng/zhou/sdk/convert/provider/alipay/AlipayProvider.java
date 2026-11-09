package cheng.zhou.sdk.convert.provider.alipay;

import cheng.zhou.sdk.convert.core.SdkProvider;
import cheng.zhou.sdk.convert.model.ConvertResult;
import cheng.zhou.sdk.convert.model.ConvertTargetInfo;
import cheng.zhou.sdk.convert.model.ParseResult;
import cheng.zhou.sdk.convert.model.ProviderInfo;
import cheng.zhou.sdk.convert.model.SampleInfo;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 支付宝 SDK 内容解析与支付链接转换
 */
@Component
public class AlipayProvider implements SdkProvider {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final String PROVIDER_ID = "alipay";
    private static final String PROVIDER_NAME = "支付宝";

    /** 网关地址与 Scheme 前缀仅用于构造页面上的解析示例，不参与用户内容生成。 */
    private static final String GATEWAY_URL = "https://openapi.alipay.com/gateway.do";
    /** 旧版 WAP 支付接口使用的 mapi 网关地址。 */
    private static final String MAPI_GATEWAY = "https://mapi.alipay.com/gateway.do";
    private static final String SCHEME_BASE = "alipays://platformapi/startapp";
    private static final String RENDER_BASE = "https://render.alipay.com/p/s/i/";
    private static final String CASHIER_APP_ID = "20000067";

    /** 转换目标 id 到中文名称的映射，同时用于校验前端传入的 target。 */
    private static final Map<String, String> CONVERT_TARGET_NAMES;

    private static final Pattern METHOD_NAME = Pattern.compile("[A-Za-z0-9_.]+");

    static {
        LinkedHashMap<String, String> names = new LinkedHashMap<String, String>();
        names.put("h5_link", "H5 支付链接");
        names.put("ios_app_pay", "iOS App 支付");
        CONVERT_TARGET_NAMES = Collections.unmodifiableMap(names);
    }

    /** 示例订单串使用 URL 编码后的真实格式，便于直接体验解析。 */
    private static final String SAMPLE_ORDER_STRING =
            "app_id=2021000000000000&method=alipay.trade.app.pay&format=json&charset=utf-8"
                    + "&sign_type=RSA2&timestamp=2026-11-09+12%3A00%3A00&version=1.0"
                    + "&notify_url=https%3A%2F%2Fexample.com%2Falipay%2Fnotify"
                    + "&biz_content=%7B%22out_trade_no%22%3A%22ORDER20261109001%22"
                    + "%2C%22total_amount%22%3A%220.01%22"
                    + "%2C%22subject%22%3A%22%E6%B5%8B%E8%AF%95%E5%95%86%E5%93%81%22"
                    + "%2C%22product_code%22%3A%22QUICK_MSECURITY_PAY%22%7D"
                    + "&sign=EXAMPLE_SIGN";

    /** WAP 支付示例订单串。 */
    private static final String SAMPLE_WAP_ORDER_STRING =
            "app_id=2021000000000000&method=alipay.trade.wap.pay&format=json&charset=utf-8"
                    + "&sign_type=RSA2&timestamp=2026-11-09+12%3A00%3A00&version=1.0"
                    + "&return_url=https%3A%2F%2Fexample.com%2Falipay%2Freturn"
                    + "&notify_url=https%3A%2F%2Fexample.com%2Falipay%2Fnotify"
                    + "&biz_content=%7B%22out_trade_no%22%3A%22ORDERWAP20261109001%22"
                    + "%2C%22total_amount%22%3A%220.01%22"
                    + "%2C%22subject%22%3A%22WAP%E6%94%AF%E4%BB%98%E6%B5%8B%E8%AF%95%22"
                    + "%2C%22product_code%22%3A%22QUICK_WAP_WAY%22%7D"
                    + "&sign=EXAMPLE_SIGN";

    private final AlipaySafePayProperties safePayProperties;
    private final List<SampleInfo> parseSamples;
    private final List<SampleInfo> convertSamples;

    public AlipayProvider(AlipaySafePayProperties safePayProperties) {
        this.safePayProperties = safePayProperties;
        this.parseSamples = createParseSamples();
        this.convertSamples = createConvertSamples();
    }

    /**
     * 组装渠道元数据。前端据此渲染渠道按钮和示例菜单。
     */
    @Override
    public ProviderInfo info() {
        ProviderInfo info = new ProviderInfo();
        info.setId(PROVIDER_ID);
        info.setName(PROVIDER_NAME);
        info.setDescription("解析支付宝订单串、Scheme 与各类链接为结构化 JSON，并把订单串转换为 H5 支付链接 / iOS SafePay 链接");
        info.setParseTypes(Arrays.asList(
                "APP 支付订单串", "alipays:// Scheme", "网关支付链接", "Render 唤起链接", "收款码链接", "JSON 参数"));
        info.setParseSamples(parseSamples);

        List<ConvertTargetInfo> convertTargets = new ArrayList<ConvertTargetInfo>();
        convertTargets.add(new ConvertTargetInfo("h5_link", "H5 支付链接",
                "APP 支付订单串换取 h5Continue 收银台链接；WAP / 旧版订单串拼支付宝网关 H5 链接"));
        convertTargets.add(new ConvertTargetInfo("ios_app_pay", "iOS App 支付",
                "APP 支付订单串生成 iOS 原生 SafePay 唤起链接（alipaymatrixbwf0cml3://）"));
        info.setConvertTargets(convertTargets);
        info.setConvertSamples(convertSamples);
        return info;
    }

    /**
     * 自动识别输入格式并解析为 JSON。
     *
     * <p>识别顺序有讲究：Render 链接本身也是 HTTPS 链接，必须排在普通网关链接
     * 之前判断；二维码链接通常不带查询串，也要在通用参数串之前识别。
     * 去掉 query 参数后仍无法命中的内容会给出格式提示，而不是静默返回空结果。</p>
     */
    @Override
    public ParseResult parse(String content) {
        if (content == null || content.trim().isEmpty()) {
            throw new IllegalArgumentException("请输入需要解析的内容");
        }
        String input = content.trim();
        if (input.startsWith("?")) {
            input = input.substring(1);
        }

        if (input.startsWith("{")) {
            return parseJson(input);
        }
        if (startsWithIgnoreCase(input, "alipays://")) {
            return parseScheme(input);
        }
        if (isRenderLink(input)) {
            return parseRenderLink(input);
        }
        if (isGatewayLink(input)) {
            return parseGatewayLink(input);
        }
        if (isQrLink(input)) {
            return parseQrLink(input);
        }
        if (input.indexOf('=') > 0) {
            return parseParamString(input);
        }
        throw new IllegalArgumentException(
                "无法识别的内容格式，请粘贴支付宝 APP 支付订单串、alipays:// 链接、网关链接、Render 链接或 JSON");
    }

    // ------------------------------------------------------------------
    // 支付链接转换
    // ------------------------------------------------------------------

    /**
     * h5_link：APP 支付串换 h5Continue 收银台，WAP / 旧版串拼支付宝网关链接。
     * ios_app_pay：APP 支付串生成 alipaymatrix SafePay 链接。
     */
    @Override
    public ConvertResult convert(String target, String content) {
        if (content == null || content.trim().isEmpty()) {
            throw new IllegalArgumentException("请输入需要转换的内容");
        }
        if (target == null || target.trim().isEmpty()) {
            throw new IllegalArgumentException("请选择转换目标");
        }
        String key = target.trim();
        String targetName = CONVERT_TARGET_NAMES.get(key);
        if (targetName == null) {
            throw new IllegalArgumentException("不支持的转换目标: " + target);
        }

        String normalized = normalizeOrderStr(content.trim());
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("内容为空，无法转换");
        }
        if (startsWithIgnoreCase(normalized, "alipays://")
                || isRenderLink(normalized) || isQrLink(normalized)) {
            throw new IllegalArgumentException("请粘贴支付宝订单串（orderStr）");
        }

        String url;
        try {
            url = "h5_link".equals(key)
                    ? buildH5Link(normalized)
                    : buildIosSafePayLink(normalized);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            String reason = e.getMessage();
            throw new IllegalStateException(reason == null || reason.isEmpty()
                    ? "支付宝收银台会话获取失败，请稍后重试"
                    : "支付宝收银台会话获取失败：" + reason, e);
        }

        return new ConvertResult(key, targetName, url);
    }

    private static String buildH5Link(String orderStr) throws Exception {
        if (isAppPay(orderStr)) {
            return AppPayConverter.buildH5Url(orderStr);
        }
        if (orderStr.startsWith("http://") || orderStr.startsWith("https://")) {
            return orderStr;
        }
        String method = paramValue(orderStr, "method");
        if (method != null && !METHOD_NAME.matcher(method).matches()) {
            throw new IllegalArgumentException("订单串 method 参数被污染（可能复制时 & 被转义）: " + method);
        }
        if (method != null && method.startsWith("alipay.trade.")) {
            return GATEWAY_URL + "?" + orderStr;
        }
        return MAPI_GATEWAY + "?" + orderStr;
    }

    private String buildIosSafePayLink(String orderStr) throws Exception {
        if (!isAppPay(orderStr)) {
            throw new IllegalArgumentException("仅 APP 支付订单串可生成 iOS SafePay 链接，WAP 订单串请选择「H5 支付链接」");
        }
        SafePayContext safePay = safePayProperties.resolve(paramValue(orderStr, "app_id"));
        return AppPayConverter.buildIosUrl(orderStr, safePay);
    }

    private static boolean isAppPay(String orderStr) {
        return "alipay.trade.app.pay".equals(paramValue(orderStr, "method"));
    }

    /** 按原始分段读取参数值（不解码），用于 method 判断。 */
    private static String paramValue(String content, String key) {
        if (content == null || content.isEmpty()) {
            return null;
        }
        for (String pair : content.split("&")) {
            int eq = pair.indexOf('=');
            if (eq >= 0 && key.equals(pair.substring(0, eq).trim())) {
                return pair.substring(eq + 1);
            }
        }
        return null;
    }

    /**
     * 规范化订单串：去 BOM/引号、剥离误粘贴的短链后缀、提取桥接 URL 中的
     * orderStr、仅在整段被 URL 编码时逐层解码。整个过程不重排参数、不修改
     * 签名内容，保证转换后链接与原订单串一致。
     *
     * <p>严禁对商户原串整体做 URLDecoder：会把签名里的 + 变成空格、把编码态
     * biz_content 解成 JSON，导致支付宝验签失败。</p>
     */
    private static String normalizeOrderStr(String orderStr) {
        if (orderStr == null || orderStr.isEmpty()) {
            return "";
        }
        String result = orderStr.trim();
        if (result.startsWith("\uFEFF")) {
            result = result.substring(1).trim();
        }
        result = trimQuotes(result);
        result = repairHtmlEscapes(result);
        result = stripGluedPayUrlSuffix(result);

        // 调用方可能把参数名一起粘进来：order_str=alipay_sdk=...&app_id=...
        if (result.startsWith("order_str=")) {
            result = result.substring("order_str=".length()).trim();
        } else if (result.startsWith("orderStr=")) {
            result = result.substring("orderStr=".length()).trim();
        }

        // 完整桥接 URL（带查询串）时取出 orderStr / order_str 参数
        if ((result.contains("?") || result.startsWith("http://") || result.startsWith("https://"))
                && (result.contains("orderStr=") || result.contains("order_str="))) {
            String extracted = extractOrderStrFromBridgeUrl(result);
            if (extracted != null && !extracted.trim().isEmpty()) {
                result = extracted.trim();
            }
        }

        // 仅当整段被 URL 编码（没有字面 &/=，却有 %26/%3D）时才逐层解码
        for (int i = 0; i < 4; i++) {
            boolean fullyEncoded = !result.contains("&") && !result.contains("=")
                    && (result.contains("%26") || result.contains("%3D"));
            if (!fullyEncoded) {
                break;
            }
            String decoded = percentDecodePreservePlus(result);
            if (decoded.equals(result) || decoded.isEmpty()) {
                break;
            }
            result = decoded.trim();
        }
        return result.trim();
    }

    /** 修复从 HTML 页面复制时被实体解码的 &（¬ify_url / ×tamp）。 */
    private static String repairHtmlEscapes(String s) {
        return s.replace("&amp;", "&")
                .replace("\u00ACify_url", "&notify_url")
                .replace("\u00D7tamp", "&timestamp");
    }

    private static String trimQuotes(String s) {
        if (s.length() >= 2) {
            char a = s.charAt(0);
            char b = s.charAt(s.length() - 1);
            if ((a == '"' && b == '"') || (a == '\'' && b == '\'')) {
                return s.substring(1, s.length() - 1).trim();
            }
        }
        return s;
    }

    /**
     * 用户常把订单串与短链粘在一起：{@code ...&version=1.0https://xxx/pay/open?t=}
     */
    private static String stripGluedPayUrlSuffix(String s) {
        if (s == null || s.isEmpty()) {
            return s;
        }
        int glued = indexOfLiteralHttpAfterParams(s);
        if (glued > 0) {
            return s.substring(0, glued).trim();
        }
        return s;
    }

    private static int indexOfLiteralHttpAfterParams(String s) {
        // notify_url 等字段里是 https%3A%2F%2F，字面 https:// 只会出现在误粘贴后缀
        int https = s.indexOf("https://");
        int http = s.indexOf("http://");
        int idx;
        if (https >= 0 && (http < 0 || https < http)) {
            idx = https;
        } else if (http >= 0) {
            idx = http;
        } else {
            return -1;
        }
        if (idx <= 0) {
            return -1;
        }
        // 必须已像 SDK 串（含 app_id=/method=/sign=），且 http 前是参数尾
        if (!(s.contains("app_id=") || s.contains("method=") || s.contains("sign="))) {
            return -1;
        }
        char prev = s.charAt(idx - 1);
        if (prev == '=' || prev == '&' || Character.isLetterOrDigit(prev) || prev == '.') {
            return idx;
        }
        return -1;
    }

    private static String extractOrderStrFromBridgeUrl(String result) {
        try {
            int q = result.indexOf('?');
            String query = q >= 0 ? result.substring(q + 1) : result;
            // 优先按原始切片取 order_str，避免 URLDecoder 把 + 变空格
            for (String key : new String[]{"orderStr", "order_str"}) {
                String marker = key + "=";
                int start = indexOfIgnoreCase(query, marker);
                if (start < 0) {
                    continue;
                }
                start += marker.length();
                int end = query.indexOf('&', start);
                String raw = end >= 0 ? query.substring(start, end) : query.substring(start);
                if (!raw.trim().isEmpty()) {
                    return percentDecodePreservePlus(raw);
                }
            }
            return firstOrderStrFromPairs(query);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String firstOrderStrFromPairs(String query) {
        if (query == null || query.isEmpty()) {
            return null;
        }
        for (String pair : query.split("&")) {
            int eq = pair.indexOf('=');
            if (eq < 0) {
                continue;
            }
            String key = pair.substring(0, eq);
            if ("orderstr".equalsIgnoreCase(key) || "order_str".equalsIgnoreCase(key)) {
                return percentDecodePreservePlus(pair.substring(eq + 1));
            }
        }
        return null;
    }

    private static int indexOfIgnoreCase(String haystack, String needle) {
        return haystack.toLowerCase(Locale.ROOT).indexOf(needle.toLowerCase(Locale.ROOT));
    }

    /**
     * 只解码 %XX，不把 + 当成空格（与 {@link URLDecoder} 不同），避免破坏签名内容。
     */
    private static String percentDecodePreservePlus(String input) {
        if (input == null || input.isEmpty() || !input.contains("%")) {
            return input;
        }
        StringBuilder out = new StringBuilder(input.length());
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if (c == '%' && i + 2 < input.length()) {
                int hi = Character.digit(input.charAt(i + 1), 16);
                int lo = Character.digit(input.charAt(i + 2), 16);
                if (hi >= 0 && lo >= 0) {
                    out.append((char) ((hi << 4) + lo));
                    i += 2;
                    continue;
                }
            }
            out.append(c);
        }
        return out.toString();
    }

    // ------------------------------------------------------------------
    // 解析
    // ------------------------------------------------------------------

    /**
     * 解析 JSON 参数（对象形式），biz_content 会被展开成嵌套对象。
     */
    private ParseResult parseJson(String input) {
        LinkedHashMap<String, Object> data;
        try {
            data = MAPPER.readValue(input, new TypeReference<LinkedHashMap<String, Object>>() {
            });
        } catch (Exception e) {
            throw new IllegalArgumentException("JSON 格式不正确: " + rootMessage(e));
        }
        normalizeBizContent(data);
        return newResult("JSON", "JSON 参数", data,
                "已解析 JSON 参数 " + data.size() + " 个");
    }

    /**
     * 解析 key=value&key=value 形式的参数串。
     *
     * <p>支付宝 APP 支付订单串使用 alipay.trade.app.pay 方法，这里据此区分
     * “APP 支付订单串”和普通的支付参数串，仅影响展示类型，不影响解析内容。</p>
     */
    private ParseResult parseParamString(String input) {
        LinkedHashMap<String, String> rawParams = parseQuery(input);
        if (rawParams.isEmpty()) {
            throw new IllegalArgumentException("参数串中没有可解析的内容");
        }
        LinkedHashMap<String, Object> data = new LinkedHashMap<String, Object>(rawParams);
        normalizeBizContent(data);

        boolean appPay = "alipay.trade.app.pay".equals(data.get("method"));
        String type = appPay ? "APP_ORDER_STRING" : "PARAM_STRING";
        String typeName = appPay ? "APP 支付订单串" : "支付参数串";
        String summary = "已解析 " + data.size() + " 个参数";
        Object method = data.get("method");
        if (method != null) {
            summary += "，method=" + method;
        }
        return newResult(type, typeName, data, summary);
    }

    /**
     * 解析 alipays:// Scheme，并在存在 url 参数时继续解析内部链接参数。
     */
    private ParseResult parseScheme(String input) {
        LinkedHashMap<String, Object> data = parseSchemeData(input);
        return newResult("ALIPAYS_SCHEME", "alipays:// Scheme", data, schemeSummary(data));
    }

    /**
     * 拆分 Scheme 的 host、path、query，并解析 url 参数指向的内部地址。
     *
     * <p>这里没有使用 {@link java.net.URI}，因为用户从日志里复制的 Scheme
     * 可能包含未编码字符，手工按 ? 和 / 拆分更宽容。</p>
     */
    private LinkedHashMap<String, Object> parseSchemeData(String input) {
        String rest = input.substring("alipays://".length());
        int hash = rest.indexOf('#');
        if (hash >= 0) {
            rest = rest.substring(0, hash);
        }

        String pathPart;
        String rawQuery;
        int question = rest.indexOf('?');
        if (question >= 0) {
            pathPart = rest.substring(0, question);
            rawQuery = rest.substring(question + 1);
        } else {
            pathPart = rest;
            rawQuery = "";
        }

        String host = pathPart;
        String path = "";
        int slash = pathPart.indexOf('/');
        if (slash >= 0) {
            host = pathPart.substring(0, slash);
            path = pathPart.substring(slash);
        }

        LinkedHashMap<String, String> params = parseQuery(rawQuery);
        LinkedHashMap<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("scheme", "alipays");
        data.put("host", host);
        if (!path.isEmpty()) {
            data.put("path", path);
        }
        data.put("params", new LinkedHashMap<String, Object>(params));

        String innerUrl = params.get("url");
        if (innerUrl != null && !innerUrl.isEmpty()) {
            data.put("innerUrl", innerUrl);
            LinkedHashMap<String, Object> innerParams =
                    new LinkedHashMap<String, Object>(parseQuery(queryOf(innerUrl)));
            if (!innerParams.isEmpty()) {
                normalizeBizContent(innerParams);
                data.put("innerParams", innerParams);
            }
        }
        return data;
    }

    /**
     * 解析 render.alipay.com 包装链接：先取出 scheme 参数，再按需解包内部的
     * alipays Scheme，还原出内部 URL 与订单参数。
     */
    private ParseResult parseRenderLink(String input) {
        LinkedHashMap<String, String> query = parseQuery(queryOf(input));
        String scheme = query.get("scheme");
        if (isBlank(scheme)) {
            throw new IllegalArgumentException("Render 链接中缺少 scheme 参数");
        }

        LinkedHashMap<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("renderLink", input);
        data.put("scheme", scheme);

        if (startsWithIgnoreCase(scheme, "alipays://")) {
            data.put("schemeParsed", parseSchemeData(scheme));
            return newResult("RENDER_LINK", "Render 唤起链接", data,
                    "已解包 Render 链接中的 alipays Scheme");
        }

        data.put("schemeParsed", parseUrlData(scheme));
        return newResult("RENDER_LINK", "Render 唤起链接", data, "已解包 Render 链接");
    }

    /**
     * 解析 openapi.alipay.com/gateway.do 网关链接。
     *
     * <p>网关链接的查询参数就是订单参数，因此直接将 query 展开为 JSON，
     * 并把 biz_content 反序列化成对象。</p>
     */
    private ParseResult parseGatewayLink(String input) {
        LinkedHashMap<String, Object> data =
                new LinkedHashMap<String, Object>(parseQuery(queryOf(input)));
        if (data.isEmpty()) {
            throw new IllegalArgumentException("网关链接中未找到参数");
        }
        normalizeBizContent(data);
        return newResult("GATEWAY_LINK", "网关支付链接", data,
                "已解析 " + data.size() + " 个网关参数");
    }

    /**
     * 解析支付宝收款码链接，提取二维码编码。
     */
    private ParseResult parseQrLink(String input) {
        String code = "";
        String lower = input.toLowerCase(Locale.ROOT);
        int index = lower.indexOf("qr.alipay.com/");
        if (index >= 0) {
            code = input.substring(index + "qr.alipay.com/".length());
            int cut = code.indexOf('?');
            if (cut < 0) {
                cut = code.indexOf('#');
            }
            if (cut >= 0) {
                code = code.substring(0, cut);
            }
            if (code.endsWith("/")) {
                code = code.substring(0, code.length() - 1);
            }
        }

        LinkedHashMap<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("url", input);
        data.put("code", code);
        return newResult("QR_LINK", "收款码链接", data, "支付宝收款码链接");
    }

    /**
     * 把普通 URL 拆成基础地址和查询参数，用于 Render 链接中非 alipays 的 scheme。
     */
    private LinkedHashMap<String, Object> parseUrlData(String url) {
        LinkedHashMap<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("url", url);
        int question = url.indexOf('?');
        data.put("base", question >= 0 ? url.substring(0, question) : url);

        LinkedHashMap<String, Object> params =
                new LinkedHashMap<String, Object>(parseQuery(queryOf(url)));
        if (!params.isEmpty()) {
            normalizeBizContent(params);
            data.put("params", params);
        }
        return data;
    }

    // ------------------------------------------------------------------
    // 通用工具
    // ------------------------------------------------------------------

    /**
     * 统一构造解析结果，避免每个分支重复设置渠道信息。
     */
    private ParseResult newResult(String type, String typeName,
                                  Map<String, Object> data, String summary) {
        ParseResult result = new ParseResult();
        result.setProvider(PROVIDER_ID);
        result.setProviderName(PROVIDER_NAME);
        result.setType(type);
        result.setTypeName(typeName);
        result.setSummary(summary);
        result.setData(data);
        return result;
    }

    /**
     * 统计 Scheme 中实际解析到的参数数量，生成页面摘要文案。
     */
    private String schemeSummary(LinkedHashMap<String, Object> data) {
        Map<String, Object> innerParams = asMap(data.get("innerParams"));
        if (innerParams != null && !innerParams.isEmpty()) {
            return "已解析内部 URL 参数 " + innerParams.size() + " 个";
        }
        Map<String, Object> params = asMap(data.get("params"));
        return "已解析 Scheme 参数 " + (params == null ? 0 : params.size()) + " 个";
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object value) {
        return value instanceof Map ? (Map<String, Object>) value : null;
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    /**
     * 把 biz_content 从 JSON 字符串归一化为对象。
     *
     * <p>从订单串或 Scheme 中解析时，biz_content 先经过 URL 解码；
     * 如果上游做过二次编码，值可能仍以 %7B 开头，这里再解码一次以增强兼容性。
     * 解析失败时保留原始字符串，不影响其余参数的展示。</p>
     */
    private void normalizeBizContent(Map<String, Object> data) {
        if (data == null) {
            return;
        }
        Object value = data.get("biz_content");
        if (!(value instanceof String)) {
            return;
        }
        String text = ((String) value).trim();
        if (text.startsWith("%7B") || text.startsWith("%7b")) {
            text = decode(text).trim();
        }
        if (text.startsWith("{")) {
            try {
                data.put("biz_content",
                        MAPPER.readValue(text, new TypeReference<LinkedHashMap<String, Object>>() {
                        }));
            } catch (Exception ignored) {
                // 非法 JSON 时保留原文，交由使用方判断
            }
        }
    }

    /**
     * 解析查询串并做 URL 解码，保持原始参数顺序。
     * 解码失败时回退为原始文本，避免个别非法转义导致整个解析失败。
     */
    private static LinkedHashMap<String, String> parseQuery(String rawQuery) {
        LinkedHashMap<String, String> result = new LinkedHashMap<String, String>();
        if (rawQuery == null || rawQuery.isEmpty()) {
            return result;
        }
        String[] pairs = rawQuery.split("&");
        for (String pair : pairs) {
            if (pair.isEmpty()) {
                continue;
            }
            int equals = pair.indexOf('=');
            String key = equals >= 0 ? pair.substring(0, equals) : pair;
            String value = equals >= 0 ? pair.substring(equals + 1) : "";
            key = decode(key);
            value = decode(value);
            if (!key.isEmpty()) {
                result.put(key, value);
            }
        }
        return result;
    }

    /**
     * 截取 URL 中 ? 之后的查询部分，并去掉 #fragment。
     */
    private static String queryOf(String url) {
        if (url == null) {
            return "";
        }
        int question = url.indexOf('?');
        if (question < 0) {
            return "";
        }
        String query = url.substring(question + 1);
        int hash = query.indexOf('#');
        if (hash >= 0) {
            query = query.substring(0, hash);
        }
        return query;
    }

    private static boolean isRenderLink(String input) {
        return input.toLowerCase(Locale.ROOT).contains("render.alipay.com/p/s/i");
    }

    private static boolean isGatewayLink(String input) {
        String lower = input.toLowerCase(Locale.ROOT);
        return lower.contains("/gateway.do")
                && (lower.contains("alipay.com") || lower.contains("alipaydev.com"));
    }

    private static boolean isQrLink(String input) {
        return input.toLowerCase(Locale.ROOT).contains("qr.alipay.com/");
    }

    private static boolean startsWithIgnoreCase(String value, String prefix) {
        return value != null && value.regionMatches(true, 0, prefix, 0, prefix.length());
    }

    private static String encode(String value) {
        try {
            return URLEncoder.encode(value, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException("当前环境不支持 UTF-8", e);
        }
    }

    private static String decode(String value) {
        try {
            return URLDecoder.decode(value, "UTF-8");
        } catch (Exception e) {
            return value;
        }
    }

    private static String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        String message = current.getMessage();
        return message == null || message.isEmpty() ? current.getClass().getSimpleName() : message;
    }

    /**
     * 构造解析示例。这里只生成填入输入框的示例文本，对外不暴露任何生成接口。
     */
    private static List<SampleInfo> createParseSamples() {
        String gateway = GATEWAY_URL + "?" + SAMPLE_ORDER_STRING;
        String scheme = SCHEME_BASE + "?appId=" + CASHIER_APP_ID + "&url=" + encode(gateway);
        String render = RENDER_BASE + "?scheme=" + encode(scheme);

        List<SampleInfo> samples = new ArrayList<SampleInfo>();
        samples.add(new SampleInfo("APP 支付订单串", SAMPLE_ORDER_STRING));
        samples.add(new SampleInfo("WAP 支付订单串", SAMPLE_WAP_ORDER_STRING));
        samples.add(new SampleInfo("网关支付链接", gateway));
        samples.add(new SampleInfo("alipays Scheme", scheme));
        samples.add(new SampleInfo("Render 唤起链接", render));
        return samples;
    }

    /**
     * 构造支付链接转换的示例，与 convertTargets 按下标一一对应。
     */
    private static List<SampleInfo> createConvertSamples() {
        List<SampleInfo> samples = new ArrayList<SampleInfo>();
        samples.add(new SampleInfo("WAP 支付订单串", SAMPLE_WAP_ORDER_STRING));
        samples.add(new SampleInfo("APP 支付订单串", SAMPLE_ORDER_STRING));
        return samples;
    }
}

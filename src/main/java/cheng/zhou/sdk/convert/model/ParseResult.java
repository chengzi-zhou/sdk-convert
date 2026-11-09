package cheng.zhou.sdk.convert.model;

import lombok.Data;

import java.util.Map;

/**
 * 解析结果：只承载 SDK 内容转换出的结构化 JSON。
 */
@Data
public class ParseResult {

    /** 渠道 id，例如 alipay。 */
    private String provider;

    /** 渠道名称，例如 支付宝。 */
    private String providerName;

    /** 机器可读的输入类型，例如 APP_ORDER_STRING。 */
    private String type;

    /** 展示用的输入类型，例如 APP 支付订单串。 */
    private String typeName;

    /** 便于展示的摘要，例如“已解析 9 个参数，method=alipay.trade.app.pay”。 */
    private String summary;

    /** 结构化后的参数，biz_content 会被展开成嵌套对象。 */
    private Map<String, Object> data;
}

package cheng.zhou.sdk.convert.core;

import cheng.zhou.sdk.convert.model.ConvertResult;
import cheng.zhou.sdk.convert.model.ParseResult;
import cheng.zhou.sdk.convert.model.ProviderInfo;

/**
 * SDK 渠道能力接口。
 *
 * <p>新增支付渠道（如微信、银联）时，实现本接口并标注
 * {@code @Component} 即可被 {@link ProviderRegistry} 自动发现，
 * 前端渠道列表和接口路由都不需要改动。</p>
 */
public interface SdkProvider {

    /**
     * 返回渠道元数据，包括名称、支持的解析格式、转换目标以及示例内容。
     * 前端启动时会调用该接口动态渲染渠道和示例，避免把渠道信息写死在页面里。
     */
    ProviderInfo info();

    /**
     * 把一段原始 SDK 内容解析成结构化 JSON。
     *
     * @param content 用户粘贴的订单串、Scheme、链接或 JSON
     * @return 解析结果，包含类型和结构化参数
     */
    ParseResult parse(String content);

    /**
     * 把订单内容转换为平台支付链接。
     *
     * @param target  转换目标，例如 h5_link、ios_app_pay
     * @param content 订单串（orderStr）或网关链接
     * @return 转换结果，包含目标类型、最终内容和使用说明
     */
    ConvertResult convert(String target, String content);
}

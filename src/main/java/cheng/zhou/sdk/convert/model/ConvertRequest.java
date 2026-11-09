package cheng.zhou.sdk.convert.model;

import lombok.Data;

/**
 * 支付链接转换请求：target 为目标类型（如 h5_link），content 为订单内容。
 */
@Data
public class ConvertRequest {

    private String provider;
    private String target;
    private String content;
}

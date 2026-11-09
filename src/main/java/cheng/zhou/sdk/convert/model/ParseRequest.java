package cheng.zhou.sdk.convert.model;

import lombok.Data;

/**
 * 解析请求：provider 为渠道 id，content 为待解析的原始内容。
 */
@Data
public class ParseRequest {

    private String provider;
    private String content;
}

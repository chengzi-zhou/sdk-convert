package cheng.zhou.sdk.convert.controller;

import cheng.zhou.sdk.convert.core.ProviderRegistry;
import cheng.zhou.sdk.convert.core.SdkProvider;
import cheng.zhou.sdk.convert.model.ApiResponse;
import cheng.zhou.sdk.convert.model.ConvertRequest;
import cheng.zhou.sdk.convert.model.ConvertResult;
import cheng.zhou.sdk.convert.model.ParseRequest;
import cheng.zhou.sdk.convert.model.ParseResult;
import cheng.zhou.sdk.convert.model.ProviderInfo;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 转换台对外的 REST 接口。
 *
 * <p>接口遵循统一响应结构 {@link ApiResponse}：code=0 表示成功，
 * 非 0 时 message 为可直接展示给用户的错误原因。</p>
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ConvertController {

    private final ProviderRegistry registry;

    /**
     * 查询所有已接入的 SDK 渠道及示例数据。
     */
    @GetMapping("/providers")
    public ApiResponse<List<ProviderInfo>> providers() {
        return ApiResponse.ok(registry.list());
    }

    /**
     * 把 SDK 内容解析为 JSON。
     *
     * <p>请求体：{@code {"provider":"alipay","content":"..."}}。</p>
     */
    @PostMapping("/parse")
    public ApiResponse<ParseResult> parse(@RequestBody ParseRequest request) {
        SdkProvider provider = registry.require(request.getProvider());
        return ApiResponse.ok(provider.parse(request.getContent()));
    }

    /**
     * 把订单内容转换为支付链接。
     *
     * <p>请求体：{@code {"provider":"alipay","target":"h5_link","content":"..."}}。</p>
     */
    @PostMapping("/convert")
    public ApiResponse<ConvertResult> convert(@RequestBody ConvertRequest request) {
        SdkProvider provider = registry.require(request.getProvider());
        return ApiResponse.ok(provider.convert(request.getTarget(), request.getContent()));
    }
}

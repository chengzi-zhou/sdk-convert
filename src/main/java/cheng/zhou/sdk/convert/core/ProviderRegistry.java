package cheng.zhou.sdk.convert.core;

import cheng.zhou.sdk.convert.model.ProviderInfo;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * SDK 渠道注册表。
 *
 * <p>Spring 启动时会注入所有 {@link SdkProvider} 实现，并按照注入顺序建立
 * id 到实现的映射。渠道数量较少，这里使用 LinkedHashMap 保证接口返回顺序稳定。</p>
 */
@Component
public class ProviderRegistry {

    private final Map<String, SdkProvider> providers = new LinkedHashMap<String, SdkProvider>();

    public ProviderRegistry(List<SdkProvider> providerList) {
        if (providerList != null) {
            for (SdkProvider provider : providerList) {
                ProviderInfo info = provider.info();
                providers.put(info.getId(), provider);
            }
        }
    }

    /**
     * 返回所有渠道的展示信息，供前端渲染渠道切换按钮。
     */
    public List<ProviderInfo> list() {
        List<ProviderInfo> result = new ArrayList<ProviderInfo>();
        for (SdkProvider provider : providers.values()) {
            result.add(provider.info());
        }
        return result;
    }

    /**
     * 根据 id 获取渠道实现。
     *
     * <p>未传 id 且系统只有一个渠道时直接返回该渠道，减少单渠道场景下的前端参数；
     * 传入未知 id 时抛出参数异常，由全局异常处理器转换为 400 响应。</p>
     */
    public SdkProvider require(String id) {
        if (id == null || id.trim().isEmpty()) {
            if (providers.size() == 1) {
                return providers.values().iterator().next();
            }
            throw new IllegalArgumentException("请选择 SDK 类型");
        }
        SdkProvider provider = providers.get(id.trim());
        if (provider == null) {
            throw new IllegalArgumentException("不支持的 SDK 类型: " + id);
        }
        return provider;
    }
}

package cheng.zhou.sdk.convert.model;

import lombok.Data;

import java.util.List;

/**
 * 渠道展示信息，前端据此渲染渠道按钮、解析示例和转换目标。
 */
@Data
public class ProviderInfo {

    /** 渠道唯一标识，也是 /api/parse 与 /api/convert 的 provider 参数。 */
    private String id;

    /** 渠道显示名称。 */
    private String name;

    /** 渠道能力说明。 */
    private String description;

    /** 支持的解析格式名称列表。 */
    private List<String> parseTypes;

    /** 解析示例，避免示例内容写死在前端。 */
    private List<SampleInfo> parseSamples;

    /** 支持的支付链接转换目标列表。 */
    private List<ConvertTargetInfo> convertTargets;

    /** 与 convertTargets 按下标一一对应的转换示例内容。 */
    private List<SampleInfo> convertSamples;
}

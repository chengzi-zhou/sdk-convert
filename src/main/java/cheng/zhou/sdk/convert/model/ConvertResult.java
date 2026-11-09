package cheng.zhou.sdk.convert.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 支付链接转换结果。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ConvertResult {

    /** 转换目标标识，例如 h5_link。 */
    private String target;

    /** 转换目标名称，例如 H5 支付链接。 */
    private String targetName;

    /** 转换出的最终内容（支付链接或订单串）。 */
    private String content;
}

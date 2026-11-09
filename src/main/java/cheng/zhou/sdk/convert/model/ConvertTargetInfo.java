package cheng.zhou.sdk.convert.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 转换目标说明，用于前端下拉框展示。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ConvertTargetInfo {

    /** 目标标识，例如 h5_link、ios_app_pay。 */
    private String id;

    /** 目标名称。 */
    private String name;

    /** 目标说明。 */
    private String description;
}

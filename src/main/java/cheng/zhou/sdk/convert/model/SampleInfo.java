package cheng.zhou.sdk.convert.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 解析示例。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SampleInfo {

    /** 示例名称，显示在示例菜单中。 */
    private String name;

    /** 示例内容，点击后填入解析编辑器。 */
    private String content;
}

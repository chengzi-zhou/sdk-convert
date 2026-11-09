package cheng.zhou.sdk.convert;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * SDK 转换台启动类。
 *
 * <p>启动后访问 http://localhost:8899 打开转换台页面，
 * 页面通过 /api/providers、/api/parse、/api/build 三个接口完成解析与生成。</p>
 */
@SpringBootApplication
public class SdkConvertApplication {

    public static void main(String[] args) {
        SpringApplication.run(SdkConvertApplication.class, args);
    }
}

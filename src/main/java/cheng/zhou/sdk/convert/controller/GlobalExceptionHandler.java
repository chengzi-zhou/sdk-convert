package cheng.zhou.sdk.convert.controller;

import cheng.zhou.sdk.convert.model.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常处理。
 *
 * <p>解析类工具的错误大多来自用户输入，因此参数异常统一返回 400 和可读的
 * 中文提示；未预期异常返回 500，避免把堆栈直接暴露到页面。</p>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 处理业务校验、格式识别等可预期的参数错误。
     */
    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Void> handleIllegalArgument(IllegalArgumentException e) {
        return ApiResponse.error(400, e.getMessage());
    }

    /**
     * 处理上游能力不可用：代理提取失败、支付宝收银台会话获取失败等。
     */
    @ExceptionHandler(IllegalStateException.class)
    @ResponseStatus(HttpStatus.BAD_GATEWAY)
    public ApiResponse<Void> handleIllegalState(IllegalStateException e) {
        return ApiResponse.error(502, e.getMessage());
    }

    /**
     * 处理请求体不是合法 JSON 的情况。
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Void> handleUnreadableBody(HttpMessageNotReadableException e) {
        return ApiResponse.error(400, "请求体格式不正确，请提交 JSON 请求");
    }

    /**
     * 兜底处理未预期异常，仅返回受控的错误信息。
     */
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ApiResponse<Void> handleException(Exception e) {
        return ApiResponse.error(500, "服务器处理失败");
    }
}

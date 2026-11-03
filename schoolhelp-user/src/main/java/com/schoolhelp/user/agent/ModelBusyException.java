package com.schoolhelp.user.agent;

/** 模型忙（限流型失败：429/1305/1302/1113/解析失败/超时等）——触发降级链切换下一个端点 */
public class ModelBusyException extends RuntimeException {
    public ModelBusyException() {
        super();
    }
}

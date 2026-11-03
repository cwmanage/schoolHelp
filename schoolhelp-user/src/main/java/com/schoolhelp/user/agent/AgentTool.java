package com.schoolhelp.user.agent;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记一个方法为 Agent 可调用的工具（类似大赛平台的"技能"注册）。
 * 带 @AgentTool 的方法会在启动时被 AgentToolRegistry 自动扫描注册，
 * 新增工具 = 写一个带注解的方法，无需修改 Agent 循环代码（开闭原则）。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface AgentTool {

    /** 工具名（LLM 调用时的 function.name），全局唯一，建议动词短语如 get_my_schedule */
    String name();

    /** 工具用途描述——LLM 靠这段话决定"什么时候该用这个工具"，务必写清楚触发场景 */
    String description();
}

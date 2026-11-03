package com.schoolhelp.user.agent;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 工具方法参数说明——参与生成 JSON Schema 的 properties/required，
 * LLM 依据 description 决定传什么值。
 * name 显式声明（不依赖编译参数 -parameters，避免构建配置差异导致参数名丢失）。
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface ToolParam {

    /** 参数名（传给 LLM 的 JSON Schema 属性名） */
    String name();

    /** 参数说明（LLM 依据它决定传值） */
    String description();

    /** 是否必填（进入 Schema 的 required 数组） */
    boolean required() default false;
}

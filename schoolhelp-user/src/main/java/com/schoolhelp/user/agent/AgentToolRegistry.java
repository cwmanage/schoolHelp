package com.schoolhelp.user.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;
import org.springframework.util.ClassUtils;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 工具注册中心：启动时扫描所有 Spring Bean 的 @AgentTool 方法，完成三件事——
 * 1. 建立 工具名 -> {bean, Method} 的执行映射（反射调用）
 * 2. 从 @ToolParam 自动生成 OpenAI 兼容的 tools JSON Schema（模型据此知道有哪些工具、怎么传参）
 * 3. 提供 execute(name, argsJson)：按 Schema 绑定参数并反射执行，结果统一序列化为字符串
 *
 * 新增工具 = 在任意 Spring Bean 上写一个带 @AgentTool 的方法，本类零改动（开闭原则）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AgentToolRegistry {

    private final ApplicationContext applicationContext;
    private final ObjectMapper objectMapper;

    /** 工具名 -> 注册项（bean + Method） */
    private final Map<String, ToolEntry> tools = new ConcurrentHashMap<>();

    /** OpenAI 兼容 tools 数组（每个元素 {type:function, function:{name,description,parameters}}） */
    private List<Map<String, Object>> openaiTools = List.of();

    private record ToolEntry(Object bean, Method method) {}

    @PostConstruct
    public void scan() {
        List<Map<String, Object>> schemas = new ArrayList<>();
        // 遍历全部单例 Bean（启动期一次性，量小）；ClassUtils.getUserClass 还原 CGLIB 代理的原始类，保证注解可见
        for (Object bean : applicationContext.getBeansOfType(Object.class).values()) {
            Class<?> target = ClassUtils.getUserClass(bean);
            for (Method m : target.getDeclaredMethods()) {
                AgentTool at = m.getAnnotation(AgentTool.class);
                if (at == null) continue;
                if (tools.containsKey(at.name())) {
                    log.warn("Agent 工具名重复，忽略后注册的: {}", at.name());
                    continue;
                }
                tools.put(at.name(), new ToolEntry(bean, m));
                schemas.add(buildSchema(at, m));
                log.info("注册 Agent 工具: {} ({} 个参数)", at.name(), m.getParameterCount());
            }
        }
        this.openaiTools = schemas;
        log.info("Agent 工具注册完成，共 {} 个", tools.size());
    }

    /** OpenAI 兼容 tools 定义（直接放入请求体的 tools 字段） */
    public List<Map<String, Object>> openaiTools() {
        return openaiTools;
    }

    public boolean has(String name) {
        return tools.containsKey(name);
    }

    /** 执行工具：argsJson 为 LLM 传来的参数 JSON；返回值统一序列化为字符串供 LLM 阅读 */
    public String execute(String name, String argsJson) {
        ToolEntry entry = tools.get(name);
        if (entry == null) {
            return "未知工具: " + name;
        }
        try {
            JsonNode args = objectMapper.readTree(argsJson == null || argsJson.isBlank() ? "{}" : argsJson);
            Method m = entry.method();
            Object[] values = new Object[m.getParameterCount()];
            Parameter[] params = m.getParameters();
            for (int i = 0; i < params.length; i++) {
                ToolParam tp = params[i].getAnnotation(ToolParam.class);
                JsonNode v = tp == null ? null : args.get(tp.name());
                values[i] = convert(v, params[i].getType());
            }
            Object out = m.invoke(entry.bean(), values);
            return out instanceof String s ? s : objectMapper.writeValueAsString(out);
        } catch (Exception e) {
            log.warn("Agent 工具执行失败 name={} err={}", name, e.getMessage());
            // 失败信息也回给 LLM——它会自行调整策略或向用户解释
            return "工具执行失败: " + e.getMessage();
        }
    }

    /** JSON 值 -> 方法参数类型的基础转换 */
    private Object convert(JsonNode v, Class<?> type) {
        if (v == null || v.isNull()) {
            // LLM 未传该参数：String 缺失统一为空串（业务方法以空串表达「默认/全部」，避免 null NPE）
            if (type == String.class) return "";
            return type == boolean.class ? false : type == int.class ? 0 : type == double.class ? 0d : null;
        }
        if (type == String.class) return v.asText();
        if (type == Integer.class || type == int.class) return v.asInt();
        if (type == Double.class || type == double.class) return v.asDouble();
        if (type == Boolean.class || type == boolean.class) return v.asBoolean();
        if (type == Long.class || type == long.class) return v.asLong();
        return objectMapper.convertValue(v, type);
    }

    /** 由 @AgentTool + @ToolParam 生成 OpenAI function Schema */
    private Map<String, Object> buildSchema(AgentTool at, Method m) {
        Map<String, Object> properties = new LinkedHashMap<>();
        List<String> required = new ArrayList<>();
        for (Parameter p : m.getParameters()) {
            ToolParam tp = p.getAnnotation(ToolParam.class);
            if (tp == null) continue;
            Map<String, Object> prop = new LinkedHashMap<>();
            prop.put("type", jsonType(p.getType()));
            prop.put("description", tp.description());
            properties.put(tp.name(), prop);
            if (tp.required()) required.add(tp.name());
        }
        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("type", "object");
        parameters.put("properties", properties);
        if (!required.isEmpty()) parameters.put("required", required);

        Map<String, Object> fn = new LinkedHashMap<>();
        fn.put("name", at.name());
        fn.put("description", at.description());
        fn.put("parameters", parameters);

        return Map.of("type", "function", "function", fn);
    }

    /** Java 类型 -> JSON Schema 类型 */
    private String jsonType(Class<?> t) {
        if (t == Integer.class || t == int.class || t == Long.class || t == long.class) return "integer";
        if (t == Double.class || t == double.class) return "number";
        if (t == Boolean.class || t == boolean.class) return "boolean";
        return "string";
    }
}

package com.enterprise.agent.impl.interaction;

import java.lang.annotation.*;

/**
 * 标记 InteractionCenterAgent 可供 LLM Function Calling 调用的工具方法。
 * （Spring AI 1.0.0-M3 尚不支持实例方法级 @Tool，此注解作为规范声明，
 *  实际路由由 InteractionCenterAgent.chat() 中的意图识别驱动。）
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Tool {
    /** 工具功能描述，供 LLM 决策使用 */
    String description() default "";
}

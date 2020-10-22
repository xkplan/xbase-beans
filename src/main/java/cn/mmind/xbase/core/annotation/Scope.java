package cn.mmind.xbase.core.annotation;

import java.lang.annotation.*;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE})
public @interface Scope {
    String scopeName() default "singleton";
}

package com.framework.annotation;

import java.lang.annotation.*;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Auth {
    boolean authenticated() default false; // cas 2 et 3
    String role() default "";               // cas 3
}

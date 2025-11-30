package com.framework.annotation;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.annotation.ElementType;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.PARAMETER) // s’applique aux paramètres de méthode
public @interface RequestParam {
    String value(); // la clé utilisée pour chercher dans request.getParameter()
}

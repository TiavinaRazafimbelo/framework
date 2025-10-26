package com.framework.annotation;

import java.io.File;
import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.List;

public class AnnotationScanner {

    public static List<Class<?>> getAnnotatedClasses(String packageName, Class<? extends Annotation> annotation) {
        List<Class<?>> result = new ArrayList<>();
        String path = packageName.replace('.', '/');
        File directory = new File("src/main/java/" + path);

        if (!directory.exists()) {
            System.out.println("Dossier introuvable : " + directory.getAbsolutePath());
            return result;
        }

        for (File file : directory.listFiles()) {
            if (file.getName().endsWith(".java")) {
                String className = packageName + "." + file.getName().replace(".java", "");
                try {
                    Class<?> clazz = Class.forName(className);
                    if (clazz.isAnnotationPresent(annotation)) {
                        result.add(clazz);
                    }
                } catch (ClassNotFoundException e) {
                    e.printStackTrace();
                }
            }
        }

        return result;
    }
}

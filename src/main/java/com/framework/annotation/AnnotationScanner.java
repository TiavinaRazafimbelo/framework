package com.framework.annotation;

import java.io.File;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.net.URL;

public class AnnotationScanner {

    public static List<Class<?>> getAnnotatedClasses(String packageName, Class<? extends Annotation> annotation) {
        List<Class<?>> result = new ArrayList<>();

        // Convertir le package en chemin de ressources
        String path = packageName.replace('.', '/');
        try {
            ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
            // Récupérer les ressources dans ce package
            Enumeration<URL> resources = classLoader.getResources(path);
            while (resources.hasMoreElements()) {
                URL resource = resources.nextElement();
                File directory = new File(resource.toURI());
                if (directory.exists()) {
                    for (File file : directory.listFiles()) {
                        if (file.getName().endsWith(".class")) {
                            String className = packageName + "." + file.getName().replace(".class", "");
                            Class<?> clazz = Class.forName(className);
                            if (clazz.isAnnotationPresent(annotation)) {
                                result.add(clazz);
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return result;
    }



    public static boolean matchUrl(String mapping, String url) {
        if (mapping == null || url == null) return false;

        // enlever "/" final
        if (mapping.endsWith("/")) mapping = mapping.substring(0, mapping.length() - 1);
        if (url.endsWith("/")) url = url.substring(0, url.length() - 1);

        String[] mapParts = mapping.split("/");
        String[] urlParts = url.split("/");

        // 📌 Cas spécial sprint3-bis :
        // "/user" doit matcher "/user/45"
        if (mapParts.length == urlParts.length - 1) {
            // mapping = "/user"
            // url = "/user/45"
            boolean samePrefix = true;
            for (int i = 0; i < mapParts.length; i++) {
                if (!mapParts[i].equals(urlParts[i])) {
                    samePrefix = false;
                    break;
                }
            }
            if (samePrefix) return true;
        }

        // Si tailles différentes → pas match
        if (mapParts.length != urlParts.length) return false;

        // comparaison segment par segment
        for (int i = 0; i < mapParts.length; i++) {
            String m = mapParts[i];
            String u = urlParts[i];

            if (m.startsWith("{") && m.endsWith("}")) {
                continue; // segment dynamique → accepté
            }

            if (!m.equals(u)) return false;
        }

        return true;
    }





    public static Method findMethodByUrl(Class<?> clazz, Class<? extends Annotation> annotation, String url) {

        Method bestMethod = null;
        boolean foundDynamic = false;

        for (Method method : clazz.getDeclaredMethods()) {
            if (!method.isAnnotationPresent(annotation)) continue;

            try {
                Annotation ann = method.getAnnotation(annotation);
                Method urlMethod = annotation.getMethod("url");
                String mapping = (String) urlMethod.invoke(ann);

                // 🔍 vérifier si l'URL reçue correspond au mapping
                if (matchUrl(mapping, url)) {

                    boolean isDynamic = mapping.contains("{");

                    // 📌 priorité au mapping dynamique
                    if (isDynamic && !foundDynamic) {
                        bestMethod = method;
                        foundDynamic = true;
                    }
                    // 📌 sinon on prend un mapping simple si aucun autre trouvé
                    else if (!foundDynamic && bestMethod == null) {
                        bestMethod = method;
                    }
                }

            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        return bestMethod;
    }





}

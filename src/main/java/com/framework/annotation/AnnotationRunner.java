package com.framework.annotation;

import java.lang.reflect.Method;

public class AnnotationRunner {
    
    // Méthode qui prend une classe et exécute automatiquement les méthodes annotées
    public static void run(Class<?> clazz) throws Exception {
        // Crée une instance de la classe passée en paramètre
        Object obj = clazz.getDeclaredConstructor().newInstance();

        // Parcourt toutes les méthodes de la classe
        for (Method method : clazz.getDeclaredMethods()) {
            // Vérifie si la méthode possède l'annotation MonAnnotation
            if (method.isAnnotationPresent(URL.class)) {
                // Récupère l'annotation
                URL annotation = method.getAnnotation(URL.class);

                // Affiche le nom de la méthode et la valeur de l'annotation
                System.out.println("Exécution automatique de la méthode : " + method.getName());
                System.out.println("Valeur : " + annotation.url());

                // Exécute la méthode
                method.invoke(obj);

                System.out.println("-------------------------");
            }
        }
    }

}

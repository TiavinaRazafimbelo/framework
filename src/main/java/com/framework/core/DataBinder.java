package com.framework.core;

import java.lang.reflect.*;
import java.util.*;

public class DataBinder {

    /**
     * Bind automatique d’un objet A PARTIR des paramètres de requête
     * Ex : e.name, e.department[0].name
     */
    public static Object bindComplexObject(Class<?> clazz, String prefix, Map<String, String[]> paramMap) throws Exception {

        Object instance = clazz.getDeclaredConstructor().newInstance();

        // Filtrer tous les paramètres qui commencent par prefix (ex : "e.")
        Map<String, String[]> filtered = new HashMap<>();
        paramMap.forEach((k, v) -> {
            if (k.startsWith(prefix + ".")) filtered.put(k.substring(prefix.length() + 1), v);
        });

        for (String key : filtered.keySet()) {
            String[] valueArr = filtered.get(key);
            String value = valueArr.length > 0 ? valueArr[0] : null;

            applyValue(instance, clazz, key, value, filtered);
        }

        return instance;
    }

    /**
     * Applique la valeur à l’objet selon le chemin :
     * department[0].name → instance.department[0].name = value
     */
    private static void applyValue(Object root, Class<?> rootClass, String path, String value, Map<String, String[]> fullMap) throws Exception {

        String[] parts = path.split("\\.");

        Object currentObject = root;
        Class<?> currentClass = rootClass;

        for (int i = 0; i < parts.length - 1; i++) {

            String part = parts[i];

            // Vérifie si c’est un tableau : ex department[0]
            if (part.matches(".+\\[\\d+\\]")) {

                String fieldName = part.substring(0, part.indexOf("["));
                int index = Integer.parseInt(part.substring(part.indexOf("[") + 1, part.indexOf("]")));

                Field field = currentClass.getDeclaredField(fieldName);
                field.setAccessible(true);

                Object arrayObj = field.get(currentObject);

                Class<?> componentType = field.getType().getComponentType();

                // Créer tableau si null
                if (arrayObj == null) {
                    int max = detectMaxIndex(fieldName, fullMap);
                    arrayObj = Array.newInstance(componentType, max + 1);
                    field.set(currentObject, arrayObj);
                }

                // Récupérer ou créer l’élément du tableau
                Object element = Array.get(arrayObj, index);
                if (element == null) {
                    element = componentType.getDeclaredConstructor().newInstance();
                    Array.set(arrayObj, index, element);
                }

                currentObject = element;
                currentClass = componentType;
            }
            else {
                // Champ simple : ex "department"
                Field field = currentClass.getDeclaredField(part);
                field.setAccessible(true);

                Object fieldValue = field.get(currentObject);

                if (fieldValue == null) {
                    fieldValue = field.getType().getDeclaredConstructor().newInstance();
                    field.set(currentObject, fieldValue);
                }

                currentObject = fieldValue;
                currentClass = fieldValue.getClass();
            }
        }

        // Maintenant on est sur le dernier segment : ex "name"
        String last = parts[parts.length - 1];

        Field field = currentClass.getDeclaredField(last);
        field.setAccessible(true);

        Object converted = convert(value, field.getType());

        field.set(currentObject, converted);
    }

    /** Trouver la taille maximale d’un tableau d’après les noms */
    private static int detectMaxIndex(String fieldName, Map<String, String[]> paramMap) {
        int max = 0;
        for (String key : paramMap.keySet()) {
            if (key.startsWith(fieldName + "[")) {
                int i1 = key.indexOf("[") + 1;
                int i2 = key.indexOf("]");
                int idx = Integer.parseInt(key.substring(i1, i2));
                if (idx > max) max = idx;
            }
        }
        return max;
    }

    /** Conversion des valeurs simples */
    private static Object convert(String raw, Class<?> type) {
        if (raw == null) return null;

        if (type == String.class) return raw;
        if (type == int.class || type == Integer.class) return Integer.parseInt(raw);
        if (type == double.class || type == Double.class) return Double.parseDouble(raw);
        if (type == float.class || type == Float.class) return Float.parseFloat(raw);
        if (type == boolean.class || type == Boolean.class) return Boolean.parseBoolean(raw);

        return null;
    }
}

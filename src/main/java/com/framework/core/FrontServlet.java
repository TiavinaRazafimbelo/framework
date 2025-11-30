package com.framework.core;


import java.io.*;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.framework.annotation.AnnotationScanner;
import com.framework.annotation.Controller;
import com.framework.annotation.GetMapping;
import com.framework.annotation.PostMapping;
import com.framework.annotation.URL;

import jakarta.servlet.*;
import jakarta.servlet.annotation.*;
import jakarta.servlet.http.*;

// @WebServlet("/")
public class FrontServlet extends HttpServlet {
    
    RequestDispatcher defaultDispatcher;

    @Override
    public void init() {
        defaultDispatcher = getServletContext().getNamedDispatcher("default");
    }

    @Override
    protected void service(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
        String path = req.getRequestURI().substring(req.getContextPath().length());
        
        boolean resourceExists = getServletContext().getResource(path) != null;

        if (resourceExists) {
            defaultServe(req, res);
        } else {
            customServe(req, res);
        }
    }


    private void customServe(HttpServletRequest req, HttpServletResponse res) throws IOException, ServletException {

        res.setContentType("text/html; charset=UTF-8");
        res.setCharacterEncoding("UTF-8");

        String url = req.getPathInfo();
        if (url == null) {
            url = req.getRequestURI().substring(req.getContextPath().length());
        }

        boolean found = false;

        // 1 – Récupérer les classes annotées @AppClass dans le projet test
        List<Class<?>> annotatedClasses = AnnotationScanner.getAnnotatedClasses("com.test.controllers", Controller.class);

        for (Class<?> clazz : annotatedClasses) {

            // 2 – Trouver la méthode correspondant à l'URL via ton annotation (@MonAnnotation)
            Method method = null;

            // 1️⃣ @URL (ancienne compatibilité)
            method = AnnotationScanner.findMethodByUrl(clazz, URL.class, url);

            // 2️⃣ Si GET, vérifier @GetMapping
            if (method == null && "GET".equalsIgnoreCase(req.getMethod())) {
                method = AnnotationScanner.findMethodByUrl(clazz, com.framework.annotation.GetMapping.class, url);
            }

            // 3️⃣ Si POST, vérifier @PostMapping
            if (method == null && "POST".equalsIgnoreCase(req.getMethod())) {
                method = AnnotationScanner.findMethodByUrl(clazz, com.framework.annotation.PostMapping.class, url);
            }


            if (method != null) {
                found = true;

                try {
                    Object instance = clazz.getDeclaredConstructor().newInstance();

                    // 3 – Exécution de la méthode du contrôleur
                    // Sprint 6 : injection sécurisée des arguments depuis request
                    // Récupération des variables dynamiques de l'URL
                    Parameter[] params = method.getParameters();
                    Object[] args = new Object[params.length];

                    // Récupérer les variables {var} depuis l'URL
                    String urlPattern;
                    if (method.isAnnotationPresent(URL.class)) {
                        urlPattern = method.getAnnotation(URL.class).url();
                    } else if (method.isAnnotationPresent(GetMapping.class)) {
                        urlPattern = method.getAnnotation(GetMapping.class).value();
                    } else if (method.isAnnotationPresent(PostMapping.class)) {
                        urlPattern = method.getAnnotation(PostMapping.class).value();
                    } else {
                        urlPattern = url; // fallback
                    }

                    Map<String, String> pathVariables = extractPathVariables(urlPattern, url);

                    for (int i = 0; i < params.length; i++) {
                        Parameter p = params[i];
                        Class<?> type = p.getType();
                        Object value = null;
                    
                        // 1️⃣ Priorité : {var} dans l'URL
                        if (pathVariables.containsKey(p.getName())) {
                            value = convert(pathVariables.get(p.getName()), type);
                        }
                    
                        // 2️⃣ Priorité : @RequestParam
                        if (value == null && p.isAnnotationPresent(com.framework.annotation.RequestParam.class)) {
                            String key = p.getAnnotation(com.framework.annotation.RequestParam.class).value();
                            String raw = req.getParameter(key);
                            if (raw != null) value = convert(raw, type);
                        }
                    
                        // 3️⃣ Valeur par défaut
                        if (value == null) {
                            if (type == int.class) value = 0;
                            else if (type == double.class) value = 0.0;
                            else if (type == float.class) value = 0f;
                            else if (type == boolean.class) value = false;
                            else value = null;
                        }
                    
                        args[i] = value;
                    }


                    // Appel de la méthode avec les arguments sécurisés
                    Object result = method.invoke(instance, args);



                    // 4 – Gestion selon type retour
                    if (result instanceof String) {
                        res.getWriter().print((String) result);
                        return;
                    }

                    else if (result instanceof ModelView) {
                        ModelView mv = (ModelView) result;
                        String viewName = mv.getView();

                        // Injecter les données dans la requête
                        for (String key : mv.getData().keySet()) {
                            req.setAttribute(key, mv.getData().get(key));
                        }

                        // Forward vers JSP
                        RequestDispatcher dispatcher = req.getRequestDispatcher("/views/" + viewName);
                        try {                  
                            dispatcher.forward(req, res);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                        
                        return;
                    }

                    else {
                        res.getWriter().println("⚠️ Type de retour non supporté : " + result);
                        return;
                    }

                } catch (Exception e) {
                    e.printStackTrace(res.getWriter());
                    return;
                }
            }
        }

        // 5 – Si aucune méthode trouvée
        if (!found) {
            res.getWriter().println("<p>Aucune methode ou classe associee à l URL : " + url + "</p>");
        }
    }




    private void defaultServe(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
        defaultDispatcher.forward(req, res);
    }



      private Object convert(String rawValue, Class<?> type) {
        try {
            if (type == String.class) return rawValue;
            if (type == int.class || type == Integer.class) return Integer.parseInt(rawValue);
            if (type == double.class || type == Double.class) return Double.parseDouble(rawValue);
            if (type == float.class || type == Float.class) return Float.parseFloat(rawValue);
            if (type == boolean.class || type == Boolean.class) return Boolean.parseBoolean(rawValue);
        } catch (Exception e) { }
        return null;
    }


    private Map<String, String> extractPathVariables(String pattern, String url) {
        Map<String, String> vars = new HashMap<>();

        // Transformer pattern /user/{id} → regex /user/([^/]+)
        String regex = pattern.replaceAll("\\{[^/]+\\}", "([^/]+)");
        Pattern p = Pattern.compile(regex);
        Matcher m = p.matcher(url);

        if (m.matches()) {
            // Extraire les noms des variables
            Matcher mNames = Pattern.compile("\\{([^/]+)\\}").matcher(pattern);
            int i = 1;
            while (mNames.find()) {
                vars.put(mNames.group(1), m.group(i++));
            }
        }

        return vars;
    }

}
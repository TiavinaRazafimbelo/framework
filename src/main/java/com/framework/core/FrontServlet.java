package com.framework.core;

import java.io.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.regex.*;

import com.framework.annotation.*;
import jakarta.servlet.*;
import jakarta.servlet.http.*;

public class FrontServlet extends HttpServlet {

    private RequestDispatcher defaultDispatcher;

    @Override
    public void init() {
        defaultDispatcher = getServletContext().getNamedDispatcher("default");
    }

    @Override
    protected void service(HttpServletRequest req, HttpServletResponse res)
            throws ServletException, IOException {

        String path = req.getRequestURI().substring(req.getContextPath().length());
        boolean resourceExists = getServletContext().getResource(path) != null;

        if (resourceExists) defaultServe(req, res);
        else customServe(req, res);
    }

    private void customServe(HttpServletRequest req, HttpServletResponse res)
            throws IOException, ServletException {

        prepareResponse(res);

        String url = extractUrl(req);
        boolean found = false;

        List<Class<?>> controllers =
                AnnotationScanner.getAnnotatedClasses("com.test.controllers", Controller.class);

        for (Class<?> clazz : controllers) {

            Method method = resolveMethod(clazz, req, url);
            if (method == null) continue;

            found = true;

            try {
                Object result = processControllerMethod(clazz, method, req, res, url);

                if (result != null) handleReturn(result, req, res);
                return;

            } catch (Exception e) {
                e.printStackTrace(res.getWriter());
                return;
            }
        }

        if (!found)
            res.getWriter().println("<p>Aucune methode pour l URL : " + url + "</p>");
    }

    /* ---------------- METHODES SEPARÉES ---------------- */

    private void prepareResponse(HttpServletResponse res) {
        res.setContentType("text/html; charset=UTF-8");
        res.setCharacterEncoding("UTF-8");
    }

    private String extractUrl(HttpServletRequest req) {
        String url = req.getPathInfo();
        if (url == null)
            url = req.getRequestURI().substring(req.getContextPath().length());
        return url;
    }

    private Method resolveMethod(Class<?> clazz, HttpServletRequest req, String url) {

        Method m = AnnotationScanner.findMethodByUrl(clazz, URL.class, url);

        if (m == null && req.getMethod().equalsIgnoreCase("GET"))
            m = AnnotationScanner.findMethodByUrl(clazz, GetMapping.class, url);

        if (m == null && req.getMethod().equalsIgnoreCase("POST"))
            m = AnnotationScanner.findMethodByUrl(clazz, PostMapping.class, url);

        return m;
    }

    private Object processControllerMethod(Class<?> clazz, Method method,
                                           HttpServletRequest req,
                                           HttpServletResponse res,
                                           String url) throws Exception {

        Object instance = clazz.getDeclaredConstructor().newInstance();
        Object[] args = resolveMethodArguments(method, req, url);
        return method.invoke(instance, args);
    }

    private Object[] resolveMethodArguments(Method method, HttpServletRequest req, String url) throws Exception {

        Parameter[] params = method.getParameters();
        Object[] args = new Object[params.length];

        String pattern = getUrlPattern(method);
        Map<String, String> pathVariables = extractPathVariables(pattern, url);

        for (int i = 0; i < params.length; i++) {

            Parameter p = params[i];
            Class<?> type = p.getType();
            Object value = null;

            if (Map.class.isAssignableFrom(type)) {
                value = buildMapParam(req);
            }
            else if (pathVariables.containsKey(p.getName())) {
                value = convert(pathVariables.get(p.getName()), type);
            }
            else if (p.isAnnotationPresent(RequestParam.class)) {
                value = convert(req.getParameter(
                        p.getAnnotation(RequestParam.class).value()), type);
            }
            else if (type.isArray()) {
                // Gestion des tableaux (ex : Employee[])
                Class<?> componentType = type.getComponentType();
                int maxIndex = detectMaxIndex(p.getName(), req.getParameterMap());
                Object array = Array.newInstance(componentType, maxIndex + 1);

                for (int j = 0; j <= maxIndex; j++) {
                    String prefix = p.getName() + "[" + j + "]";
                    Object element = DataBinder.bindComplexObject(componentType, prefix, req.getParameterMap());
                    Array.set(array, j, element);
                }
                value = array;
            }
            else if (isComplexObject(type)) {
                value = DataBinder.bindComplexObject(type, p.getName(), req.getParameterMap());
            }
            else {
                // Récupération générique depuis les paramètres POST
                String param = req.getParameter(p.getName());
                if (param != null) value = convert(param, type);
            }

            if (value == null) value = defaultValue(type);
            args[i] = value;
        }
        return args;
    }

    /** Détecte le plus grand index présent dans paramMap pour un tableau donné */
    private int detectMaxIndex(String paramName, Map<String, String[]> paramMap) {
        int max = -1;
        for (String key : paramMap.keySet()) {
            if (key.startsWith(paramName + "[")) {
                int i1 = key.indexOf("[") + 1;
                int i2 = key.indexOf("]");
                int idx = Integer.parseInt(key.substring(i1, i2));
                if (idx > max) max = idx;
            }
        }
        return max;
    }


    private boolean isComplexObject(Class<?> type) {
        return !type.isPrimitive()
                && type != String.class
                && !Number.class.isAssignableFrom(type)
                && !Map.class.isAssignableFrom(type);
    }

    private Map<String, Object> buildMapParam(HttpServletRequest req) {
        Map<String, Object> map = new HashMap<>();
        req.getParameterMap().forEach((k, v) -> map.put(k, v.length == 1 ? v[0] : v));
        return map;
    }

    private String getUrlPattern(Method method) {
        if (method.isAnnotationPresent(URL.class)) return method.getAnnotation(URL.class).url();
        if (method.isAnnotationPresent(GetMapping.class)) return method.getAnnotation(GetMapping.class).value();
        if (method.isAnnotationPresent(PostMapping.class)) return method.getAnnotation(PostMapping.class).value();
        return "";
    }

    private void handleReturn(Object result, HttpServletRequest req, HttpServletResponse res)
            throws ServletException, IOException {

        if (result instanceof String) {
            res.getWriter().print(result);
            return;
        }

        if (result instanceof ModelView) {
            ModelView mv = (ModelView) result;

            mv.getData().forEach(req::setAttribute);

            req.getRequestDispatcher("/views/" + mv.getView())
                    .forward(req, res);
            return;
        }

        res.getWriter().println(" Type de retour non supporte : " + result);
    }

    private void defaultServe(HttpServletRequest req, HttpServletResponse res)
            throws ServletException, IOException {
        defaultDispatcher.forward(req, res);
    }

    private Object convert(String raw, Class<?> type) {
        try {
            if (type == String.class) return raw;
            if (type == int.class || type == Integer.class) return Integer.parseInt(raw);
            if (type == double.class || type == Double.class) return Double.parseDouble(raw);
            if (type == float.class || type == Float.class) return Float.parseFloat(raw);
            if (type == boolean.class || type == Boolean.class) return Boolean.parseBoolean(raw);
        } catch (Exception e) {}
        return null;
    }

    private Object defaultValue(Class<?> t) {
        if (t == int.class) return 0;
        if (t == double.class) return 0.0;
        if (t == float.class) return 0f;
        if (t == boolean.class) return false;
        return null;
    }

    private Map<String, String> extractPathVariables(String pattern, String url) {
        Map<String, String> vars = new HashMap<>();
        String regex = pattern.replaceAll("\\{[^/]+\\}", "([^/]+)");

        Pattern p = Pattern.compile(regex);
        Matcher m = p.matcher(url);

        if (m.matches()) {
            Matcher mNames = Pattern.compile("\\{([^/]+)\\}").matcher(pattern);
            int i = 1;
            while (mNames.find()) vars.put(mNames.group(1), m.group(i++));
        }

        return vars;
    }
}

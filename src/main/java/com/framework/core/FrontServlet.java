package com.framework.core;

import java.io.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.regex.*;

import com.framework.annotation.*;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import jakarta.servlet.*;
import jakarta.servlet.annotation.MultipartConfig;
import jakarta.servlet.http.*;

@MultipartConfig
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
                // 🔐 CHECK AUTH
                if (!checkAuthorization(method, req)) {
                    res.setStatus(HttpServletResponse.SC_FORBIDDEN);
                    res.getWriter().println("Accès refusé");
                    return;
                }

                ControllerResult cr = processControllerMethod(clazz, method, req, res, url);
                handleReturn(cr, req, res, method);

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

    private ControllerResult processControllerMethod(Class<?> clazz,
                                                     Method method,
                                                     HttpServletRequest req,
                                                     HttpServletResponse res,
                                                     String url) throws Exception {

        Object instance = clazz.getDeclaredConstructor().newInstance();
        Object[] args = resolveMethodArguments(method, req, url);
        Object result = method.invoke(instance, args);

        return new ControllerResult(result, args);
    }



    private boolean checkAuthorization(Method method, HttpServletRequest req) {

        if (!method.isAnnotationPresent(Auth.class))
            return true; // accès libre

        Auth auth = method.getAnnotation(Auth.class);
        HttpSession session = req.getSession(false);

        String authKey = getServletContext().getInitParameter("auth.session.key");
        String roleKey = getServletContext().getInitParameter("role.session.key");

        // Cas 2 et 3 : authentification requise
        if (auth.authenticated()) {
            if (session == null) return false;

            Object isAuth = session.getAttribute(authKey);
            if (!(isAuth instanceof Boolean) || !((Boolean) isAuth))
                return false;
        }

        // Cas 3 : rôle requis
        if (!auth.role().isEmpty()) {
            Object role = session.getAttribute(roleKey);
            if (role == null || !auth.role().equals(role.toString()))
                return false;
        }

        return true;
    }




    private Object[] resolveMethodArguments(Method method, HttpServletRequest req, String url) throws Exception {
        Parameter[] params = method.getParameters();
        Object[] args = new Object[params.length];
    
        String pattern = getUrlPattern(method);
        Map<String, String> pathVariables = extractPathVariables(pattern, url);
    
        boolean isMultipart = req.getContentType() != null &&
                req.getContentType().toLowerCase().startsWith("multipart/");
    
        Map<String, Part> partsMap = new HashMap<>();
        Map<String, byte[]> bytesMap = new HashMap<>();
    
        // ✅ Dossier uploads persistant dans le projet (pas le tmp de Tomcat)
        File projectDir = new File(System.getProperty("user.dir"));
        File uploadDir = new File(projectDir, "uploads");
        if (!uploadDir.exists()) uploadDir.mkdirs();
    
        if (isMultipart) {
            for (Part p : req.getParts()) {
                String originalName = p.getSubmittedFileName();
                if (originalName != null) {
                    // Nettoyage du nom
                    String cleanName = originalName.replaceAll("[^a-zA-Z0-9\\.\\-_]", "_");
                    // Nom unique pour éviter les conflits
                    String finalName = System.currentTimeMillis() + "_" + cleanName;
                    File fileOnDisk = new File(uploadDir, finalName);
                
                    // ⚡ Sauvegarde physique du fichier
                    try (InputStream is = p.getInputStream();
                         OutputStream os = new FileOutputStream(fileOnDisk)) {
                        byte[] buffer = new byte[8192];
                        int len;
                        while ((len = is.read(buffer)) > 0) {
                            os.write(buffer, 0, len);
                        }
                    }
                
                    // Remplace Part par le fichier sur disque si nécessaire
                    partsMap.put(p.getName(), p);
                }
            
                // Stockage en mémoire (byte[]) pour Map<String, byte[]>
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                try (InputStream is = p.getInputStream()) {
                    byte[] buffer = new byte[8192];
                    int len;
                    while ((len = is.read(buffer)) > 0) {
                        baos.write(buffer, 0, len);
                    }
                }
                bytesMap.put(p.getName(), baos.toByteArray());
            }
        }
    
        // Traitement des paramètres de la méthode
        for (int i = 0; i < params.length; i++) {
            Parameter p = params[i];
            Class<?> type = p.getType();
            Object value = null;
        
            if (Map.class.isAssignableFrom(type) && !isMultipart) {
                value = buildMapParam(req);
            } else if (type == Part.class && isMultipart) {
                value = partsMap.get(p.getName());
            } else if (type == Part[].class && isMultipart) {
                List<Part> list = new ArrayList<>();
                for (String key : partsMap.keySet()) {
                    if (key.equals(p.getName()) || key.startsWith(p.getName() + "[")) {
                        list.add(partsMap.get(key));
                    }
                }
                value = list.toArray(new Part[0]);
            } else if (type == Map.class && isMultipart) {
                value = bytesMap;
            } else if (pathVariables.containsKey(p.getName())) {
                value = convert(pathVariables.get(p.getName()), type);
            } else if (p.isAnnotationPresent(RequestParam.class)) {
                value = convert(req.getParameter(p.getAnnotation(RequestParam.class).value()), type);
            } else if (type.isArray()) {
                Class<?> componentType = type.getComponentType();
                int maxIndex = detectMaxIndex(p.getName(), req.getParameterMap());
                Object array = Array.newInstance(componentType, maxIndex + 1);
                for (int j = 0; j <= maxIndex; j++) {
                    String prefix = p.getName() + "[" + j + "]";
                    Object element = DataBinder.bindComplexObject(componentType, prefix, req.getParameterMap());
                    Array.set(array, j, element);
                }
                value = array;
            } // ================= SESSION =================
            else if (type == FrameworkSession.class) {
                HttpSession httpSession = req.getSession(true);
                value = new FrameworkSession(httpSession);
            } else if (isComplexObject(type)) {
                value = DataBinder.bindComplexObject(type, p.getName(), req.getParameterMap());
            } else {
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

    private void handleReturn(ControllerResult cr,
                              HttpServletRequest req,
                              HttpServletResponse res,
                              Method method)
            throws IOException, ServletException {

        Object result = cr.returnValue();
        Object[] args = cr.args();

        /* ================= JSON ================= */
        if (method.isAnnotationPresent(Json.class)) {

            res.setContentType("application/json;charset=UTF-8");
            res.setCharacterEncoding("UTF-8");

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("status", "success");
            response.put("code", 200);

            /* ===== arguments ===== */
            Parameter[] params = method.getParameters();
            Map<String, Object> argsMap = new LinkedHashMap<>();

            for (int i = 0; i < params.length; i++) {
                argsMap.put(params[i].getName(), args[i]);
            }

            response.put("args", argsMap);

            /* ===== data ===== */
            Object data;

            if (result instanceof ModelView mv) {
                data = mv.getData();
            }
            else if (result instanceof List<?> list) {
                Map<String, Object> listData = new LinkedHashMap<>();
                listData.put("count", list.size());
                listData.put("result", list);
                data = listData;
            }
            else {
                data = result;
            }

            response.put("data", data);

            Gson gson = new GsonBuilder().serializeNulls().create();
            res.getWriter().print(gson.toJson(response));
            return;
        }

        /* ================= JSP / NORMAL ================= */
        if (result instanceof String) {
            res.getWriter().print(result);
        }
        else if (result instanceof ModelView mv) {
            mv.getData().forEach(req::setAttribute);
            req.getRequestDispatcher("/views/" + mv.getView()).forward(req, res);
        }
        else {
            res.getWriter().println("Type de retour non supporté : " + result);
        }
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

package com.framework.core;


import java.io.*;
import java.lang.reflect.Method;
import java.util.List;

import com.framework.annotation.AnnotationScanner;
import com.framework.annotation.Controller;
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
            Method method = AnnotationScanner.findMethodByUrl(clazz, URL.class, url);

            if (method != null) {
                found = true;

                try {
                    Object instance = clazz.getDeclaredConstructor().newInstance();

                    // 3 – Exécution de la méthode du contrôleur
                    Object result = method.invoke(instance);

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
}
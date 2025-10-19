package com.framework.core;


import java.io.*;
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

    private void customServe(HttpServletRequest req, HttpServletResponse res) throws IOException {
        try (PrintWriter out = res.getWriter()) {
            String url = req.getRequestURI();
            res.setContentType("text/html;charset=UTF-8");
            out.println("<html><head><title>FrontServlet</title></head><body>");
            out.println("<h1>URL demandée : " + url + "</h1>");
            out.println("<p>Ceci est le FrontServlet. Aucune ressource trouvée pour cette URL.</p>");
            out.println("</body></html>");
        }
    }

    private void defaultServe(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
        defaultDispatcher.forward(req, res);
    }
}
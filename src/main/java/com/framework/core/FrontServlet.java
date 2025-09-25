package com.framework.core;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;

public class FrontServlet extends HttpServlet {

    @Override
    protected void service(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        // Pour Sprint 1, on fait simple : juste afficher qu'on est dans FrontServlet
        resp.setContentType("text/plain");
        resp.getWriter().println("Hello from FrontServlet! You requested: " + req.getRequestURI());
    }
}

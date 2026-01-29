package com.framework.core;

import jakarta.servlet.http.HttpSession;

public class FrameworkSession {

    private HttpSession session;

    public FrameworkSession(HttpSession session) {
        this.session = session;
    }

    // Ajouter / modifier
    public void set(String key, Object value) {
        session.setAttribute(key, value);
    }

    // Lire
    public Object get(String key) {
        return session.getAttribute(key);
    }

    // Supprimer une variable
    public void remove(String key) {
        session.removeAttribute(key);
    }

    // Supprimer toute la session
    public void invalidate() {
        session.invalidate();
    }
}

package id.my.agungdh.auth.infrastructure.security;

import id.my.agungdh.user.domain.model.User;
import jakarta.enterprise.context.RequestScoped;

/**
 * Request-scoped holder for authenticated user — populated by AuthFilter.
 */
@RequestScoped
public class AuthContext {

    private User user;
    private String token;

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public boolean isAuthenticated() {
        return user != null;
    }
}

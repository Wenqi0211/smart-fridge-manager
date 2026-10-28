package api.security;

import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.concurrent.locks.ReentrantLock;

/** Runs after signature validation and before controllers touch user-scoped mutable services. */
public class UserOperationFilter extends OncePerRequestFilter {
    private final AuthenticatedUserScope scope;

    public UserOperationFilter(AuthenticatedUserScope scope) {
        this.scope = scope;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        if (!(SecurityContextHolder.getContext().getAuthentication() instanceof JwtAuthenticationToken)) {
            chain.doFilter(request, response);
            return;
        }
        ReentrantLock lock = scope.currentLock();
        lock.lock();
        try {
            chain.doFilter(request, response);
        } finally {
            lock.unlock();
        }
    }
}

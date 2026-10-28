package api.security;

import org.springframework.beans.factory.ObjectFactory;
import org.springframework.beans.factory.config.Scope;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Keeps the existing stateful services isolated by verified JWT subject, never by a request
 * parameter. State is process-local and is deliberately not represented as durable storage.
 */
public class AuthenticatedUserScope implements Scope {
    private final Map<String, UserObjects> users = new ConcurrentHashMap<>();

    private String subject() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            throw new IllegalStateException("Authenticated user scope requires a verified identity");
        }
        return authentication.getName();
    }

    private UserObjects current() {
        return users.computeIfAbsent(subject(), ignored -> new UserObjects());
    }

    /** Serialize one user's HTTP operations so read-modify-write grocery merges cannot race. */
    public ReentrantLock currentLock() {
        return current().lock;
    }

    @Override
    public Object get(String name, ObjectFactory<?> objectFactory) {
        return current().objects.computeIfAbsent(name, ignored -> objectFactory.getObject());
    }

    @Override
    public Object remove(String name) {
        return current().objects.remove(name);
    }

    @Override
    public void registerDestructionCallback(String name, Runnable callback) {
        // Objects live for the process lifetime; checkout clears only the current user's state.
    }

    @Override
    public Object resolveContextualObject(String key) {
        return "subject".equals(key) ? subject() : null;
    }

    @Override
    public String getConversationId() {
        return subject();
    }

    private static class UserObjects {
        private final Map<String, Object> objects = new ConcurrentHashMap<>();
        private final ReentrantLock lock = new ReentrantLock();
    }
}

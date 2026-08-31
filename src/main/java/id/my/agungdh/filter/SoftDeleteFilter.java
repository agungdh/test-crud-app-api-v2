package id.my.agungdh.filter;

import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.inject.Inject;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.ext.Provider;
import org.hibernate.Session;

@Provider
@ApplicationScoped
@Priority(1)
public class SoftDeleteFilter implements ContainerRequestFilter {

    @Inject
    EntityManager em;

    @Override
    public void filter(ContainerRequestContext requestContext) {
        try {
            Session session = em.unwrap(Session.class);
            // default exclude soft-deleted; enable filter for every request
            if (session.getEnabledFilter("softDeleteFilter") == null) {
                session.enableFilter("softDeleteFilter");
            }
        } catch (Exception ignored) {
            // outside Hibernate session (e.g., non-DB endpoint) - ignore
        }
    }
}

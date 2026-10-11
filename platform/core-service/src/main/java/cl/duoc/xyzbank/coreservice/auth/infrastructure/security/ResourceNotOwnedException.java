package cl.duoc.xyzbank.coreservice.auth.infrastructure.security;

import org.springframework.security.access.AccessDeniedException;

/** The caller is authenticated but does not own the requested resource; answered as not found. */
public class ResourceNotOwnedException extends AccessDeniedException {

    public ResourceNotOwnedException() {
        super("Resource not found");
    }
}

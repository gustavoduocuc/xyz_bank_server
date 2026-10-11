package cl.duoc.xyzbank.authserver.sessions.application.ports;

/** Tells the platform something security-relevant happened to a customer's login. */
public interface SecurityAlertPublisher {

    void refreshTokenReuse(String customerId);
}

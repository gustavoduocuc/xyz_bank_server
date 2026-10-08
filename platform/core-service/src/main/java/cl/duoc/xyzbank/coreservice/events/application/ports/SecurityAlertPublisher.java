package cl.duoc.xyzbank.coreservice.events.application.ports;

public interface SecurityAlertPublisher {

    void cardLocked(String customerId);
}

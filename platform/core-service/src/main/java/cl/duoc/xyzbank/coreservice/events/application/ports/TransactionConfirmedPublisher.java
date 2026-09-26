package cl.duoc.xyzbank.coreservice.events.application.ports;

import cl.duoc.xyzbank.coreservice.events.application.dto.TransactionConfirmed;

public interface TransactionConfirmedPublisher {

    void publish(TransactionConfirmed event);
}

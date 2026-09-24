package cl.duoc.xyzbank.coreservice.interests.application.ports;

import cl.duoc.xyzbank.coreservice.interests.application.dto.InterestCreditRejected;

public interface InterestCreditResultPublisher {

    void reject(InterestCreditRejected rejection);
}

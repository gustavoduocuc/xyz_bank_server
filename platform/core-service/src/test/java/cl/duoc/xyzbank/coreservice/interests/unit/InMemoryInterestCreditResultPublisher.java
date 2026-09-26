package cl.duoc.xyzbank.coreservice.interests.unit;

import cl.duoc.xyzbank.coreservice.interests.application.dto.InterestCreditRejected;
import cl.duoc.xyzbank.coreservice.interests.application.ports.InterestCreditResultPublisher;

import java.util.ArrayList;
import java.util.List;

public class InMemoryInterestCreditResultPublisher implements InterestCreditResultPublisher {

    private final List<InterestCreditRejected> rejections = new ArrayList<>();

    @Override
    public void reject(InterestCreditRejected rejection) {
        rejections.add(rejection);
    }

    public List<InterestCreditRejected> rejections() {
        return List.copyOf(rejections);
    }
}

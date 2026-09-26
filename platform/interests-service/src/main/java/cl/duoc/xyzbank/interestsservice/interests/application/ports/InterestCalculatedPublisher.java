package cl.duoc.xyzbank.interestsservice.interests.application.ports;

import cl.duoc.xyzbank.interestsservice.interests.application.dto.InterestCalculatedNotice;

public interface InterestCalculatedPublisher {

    void publish(InterestCalculatedNotice notice);
}

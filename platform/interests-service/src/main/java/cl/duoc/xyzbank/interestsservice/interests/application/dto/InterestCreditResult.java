package cl.duoc.xyzbank.interestsservice.interests.application.dto;

import cl.duoc.xyzbank.interestsservice.interests.domain.entities.InterestCalculationStatus;

public record InterestCreditResult(String eventId, InterestCalculationStatus status, String reason) {
}

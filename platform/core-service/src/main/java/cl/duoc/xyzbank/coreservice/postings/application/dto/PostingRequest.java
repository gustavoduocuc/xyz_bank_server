package cl.duoc.xyzbank.coreservice.postings.application.dto;

import java.util.List;

public record PostingRequest(String paymentId, List<PostingEntry> entries) {
}

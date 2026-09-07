package cl.duoc.xyzbank.coreservice.accounts.application.dto;

public record CustomerProfileResponse(
        String id,
        String fullName,
        String email) {
}

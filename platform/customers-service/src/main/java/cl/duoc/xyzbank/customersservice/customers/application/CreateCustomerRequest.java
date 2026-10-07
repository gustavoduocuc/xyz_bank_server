package cl.duoc.xyzbank.customersservice.customers.application;

public record CreateCustomerRequest(String fullName, String email, String phone, String address) {
}

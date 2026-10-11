package cl.duoc.xyzbank.customersservice.customers.application;

import cl.duoc.xyzbank.customersservice.customers.domain.Customer;

public record CustomerResponse(String id, String fullName, String email, String phone, String address, long version) {

    static CustomerResponse from(Customer customer) {
        return new CustomerResponse(
                customer.id().toString(),
                customer.fullName(),
                customer.email(),
                customer.phone(),
                customer.address(),
                customer.version());
    }
}

package cl.duoc.xyzbank.bffweb.dashboard.application.ports;

import cl.duoc.xyzbank.bffweb.dashboard.application.dto.CustomerProfile;

public interface CustomerProfilePort {

    CustomerProfile fetchProfile(String customerId);
}

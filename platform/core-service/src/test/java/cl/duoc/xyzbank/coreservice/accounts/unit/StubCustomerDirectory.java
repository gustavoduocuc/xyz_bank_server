package cl.duoc.xyzbank.coreservice.accounts.unit;

import cl.duoc.xyzbank.coreservice.accounts.application.ports.CustomerDirectory;
import cl.duoc.xyzbank.coreservice.accounts.application.ports.CustomerDirectoryUnavailableException;

import java.util.HashSet;
import java.util.Set;

public class StubCustomerDirectory implements CustomerDirectory {

    private final Set<String> knownCustomers = new HashSet<>();
    private boolean unavailable;
    private int lookups;

    public void knows(String customerId) {
        knownCustomers.add(customerId);
    }

    public void goDown() {
        unavailable = true;
    }

    public int lookups() {
        return lookups;
    }

    @Override
    public boolean exists(String customerId) {
        lookups++;
        if (unavailable) {
            throw new CustomerDirectoryUnavailableException("customers-service is unavailable");
        }
        return knownCustomers.contains(customerId);
    }
}

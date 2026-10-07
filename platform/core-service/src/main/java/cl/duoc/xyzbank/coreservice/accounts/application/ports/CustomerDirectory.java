package cl.duoc.xyzbank.coreservice.accounts.application.ports;

/** Customer data is owned by customers-service; core-service only asks whether a customer exists. */
public interface CustomerDirectory {

    /**
     * @throws CustomerDirectoryUnavailableException when the answer cannot be obtained
     */
    boolean exists(String customerId);
}

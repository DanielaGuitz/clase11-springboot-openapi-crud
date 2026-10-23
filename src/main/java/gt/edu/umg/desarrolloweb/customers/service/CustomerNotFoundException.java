package gt.edu.umg.desarrolloweb.customers.service;

/** Thrown when a customer id does not exist. Translated to HTTP 404 by the exception handler. */
public class CustomerNotFoundException extends RuntimeException {

    public CustomerNotFoundException(Long customerId) {
        super("Customer " + customerId + " not found");
    }
}

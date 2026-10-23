package gt.edu.umg.desarrolloweb.customers.service;

/** Thrown when another customer already uses the meter code. Translated to HTTP 409. */
public class DuplicateMeterCodeException extends RuntimeException {

    public DuplicateMeterCodeException(String meterCode) {
        super("Meter code " + meterCode + " is already assigned to another customer");
    }
}

package gt.edu.umg.desarrolloweb.customers.repository;

import gt.edu.umg.desarrolloweb.customers.entity.Customer;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repository pattern (Clase 7) the Spring Data way: we only declare the interface,
 * Spring generates the implementation at runtime.
 *
 * JpaRepository already gives us: findAll, findById, save, deleteById, existsById...
 * Methods below are "derived queries": Spring builds the SQL from the method name.
 * Every query runs as a prepared statement, so there is no SQL injection by concatenation.
 */
public interface CustomerRepository extends JpaRepository<Customer, Long> {

    // SELECT ... FROM customers WHERE active = ?
    List<Customer> findByActive(boolean active);

    // SELECT COUNT(*) > 0 FROM customers WHERE meter_code = ?
    boolean existsByMeterCode(String meterCode);

    // Same as above but excluding one id (used when updating a customer)
    boolean existsByMeterCodeAndIdNot(String meterCode, Long id);
}

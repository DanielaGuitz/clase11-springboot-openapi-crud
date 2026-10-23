package gt.edu.umg.desarrolloweb.pagos.repository;

import gt.edu.umg.desarrolloweb.pagos.entity.Pago;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data repository for the "Pagos" table.
 *
 * findByEstado is a derived query: Spring builds
 * SELECT ... FROM Pagos WHERE estado = ? as a prepared statement.
 */
public interface PagoRepository extends JpaRepository<Pago, Integer> {

    List<Pago> findByEstado(String estado);
}

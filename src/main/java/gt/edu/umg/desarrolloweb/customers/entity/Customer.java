package gt.edu.umg.desarrolloweb.customers.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

/**
 * JPA entity: maps ONE row of the "customers" table to ONE Java object.
 *
 * Layer: persistence. This class never leaves the service layer; the REST layer works
 * with the generated DTOs (CustomerDto / CustomerRequestDto). Keeping them separate means
 * we can change the table without breaking the public contract (and vice versa).
 */
@Entity
@Table(name = "customers")
public class Customer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY) // AUTO_INCREMENT in MariaDB
    private Long id;

    @Column(name = "full_name", nullable = false, length = 120)
    private String fullName;

    @Column(name = "meter_code", nullable = false, unique = true, length = 20)
    private String meterCode;

    @Column(nullable = false, length = 200)
    private String address;

    @Column(length = 120)
    private String email;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * JPA lifecycle callback: runs right before the INSERT.
     * Dates are stored in UTC (same as the MariaDB container clock) to avoid timezone surprises.
     */
    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now(ZoneOffset.UTC);
    }

    // JPA needs a no-args constructor; getters/setters are written explicitly (no Lombok)
    // so students see exactly what the framework uses.

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getMeterCode() {
        return meterCode;
    }

    public void setMeterCode(String meterCode) {
        this.meterCode = meterCode;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}

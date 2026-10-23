package gt.edu.umg.desarrolloweb.customers.mapper;

import gt.edu.umg.desarrolloweb.customers.api.dto.CustomerDto;
import gt.edu.umg.desarrolloweb.customers.api.dto.CustomerRequestDto;
import gt.edu.umg.desarrolloweb.customers.entity.Customer;
import java.time.ZoneOffset;
import org.springframework.stereotype.Component;

/**
 * Translates between the generated DTOs (contract) and the JPA entity (database).
 *
 * Written by hand on purpose so the mapping is visible. In real projects a library
 * such as MapStruct usually generates this class.
 */
@Component
public class CustomerMapper {

    /** Copies the request fields onto an entity (new or existing). */
    public void copyToEntity(CustomerRequestDto request, Customer target) {
        target.setFullName(request.getFullName().trim());
        target.setMeterCode(request.getMeterCode());
        target.setAddress(request.getAddress().trim());
        target.setEmail(request.getEmail());
        // "active" is optional in the spec (default true): only an explicit false deactivates.
        // Boolean.FALSE.equals(...) avoids a NullPointerException when the field is null.
        target.setActive(!Boolean.FALSE.equals(request.getActive()));
    }

    public CustomerDto toDto(Customer customer) {
        return new CustomerDto()
                .id(customer.getId())
                .fullName(customer.getFullName())
                .meterCode(customer.getMeterCode())
                .address(customer.getAddress())
                .email(customer.getEmail())
                .active(customer.isActive())
                // The DB stores a UTC LocalDateTime; the contract says "date-time" => OffsetDateTime
                .createdAt(customer.getCreatedAt() == null ? null
                        : customer.getCreatedAt().atOffset(ZoneOffset.UTC));
    }
}

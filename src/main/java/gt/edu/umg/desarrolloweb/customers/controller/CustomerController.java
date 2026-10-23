package gt.edu.umg.desarrolloweb.customers.controller;

import gt.edu.umg.desarrolloweb.customers.api.dto.CustomerDto;
import gt.edu.umg.desarrolloweb.customers.api.dto.CustomerRequestDto;
import gt.edu.umg.desarrolloweb.customers.api.spec.CustomersApi;
import gt.edu.umg.desarrolloweb.customers.service.CustomerService;
import umg.edu.gt.main.Main;

import java.math.BigDecimal;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST layer. Implements the interface GENERATED from customers-api.yaml.
 *
 * Notice what is NOT here: no @GetMapping, @PostMapping, @PathVariable, @Valid...
 * All of that is already declared in CustomersApi (target/generated-sources/openapi),
 * so the URLs, HTTP methods and validations always match the contract.
 * If the YAML changes (e.g. a new operation), this class stops compiling until we
 * implement it: the compiler enforces the contract.
 *
 * The controller stays thin: it translates HTTP <-> service calls, nothing more.
 */
@RestController
public class CustomerController implements CustomersApi {

    private final CustomerService customerService;

    public CustomerController(CustomerService customerService) {
        this.customerService = customerService;
    }

    @Override
    public ResponseEntity<List<CustomerDto>> listCustomers(Boolean active) {
        return ResponseEntity.ok(customerService.list(active));
    }

    @Override
    public ResponseEntity<CustomerDto> getCustomer(Long customerId) {
        return ResponseEntity.ok(customerService.get(customerId));
    }

    @Override
    public ResponseEntity<CustomerDto> createCustomer(CustomerRequestDto customerRequestDto) {
        CustomerDto created = customerService.create(customerRequestDto);
        // 201 Created + Location header pointing to the new resource (REST convention)
        return ResponseEntity.created(URI.create("/api/v1/customers/" + created.getId())).body(created);
    }

    @Override
    public ResponseEntity<CustomerDto> updateCustomer(Long customerId, CustomerRequestDto customerRequestDto) {
        return ResponseEntity.ok(customerService.update(customerId, customerRequestDto));
    }

    @Override
    public ResponseEntity<Void> deleteCustomer(Long customerId) {
        customerService.delete(customerId);
        return ResponseEntity.noContent().build(); // 204, no body
    }
    
    @GetMapping("/mostrarMensaje")
    public String greeting(@RequestParam(value = "name", defaultValue = "Mundo") String name) {
        Main m = new Main();
        String test = m.mostrarMensaje("Que hace");
    	return test;
    }
    

    

}

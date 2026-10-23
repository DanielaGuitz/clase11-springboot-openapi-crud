package gt.edu.umg.desarrolloweb.customers.service;

import gt.edu.umg.desarrolloweb.customers.api.dto.CustomerDto;
import gt.edu.umg.desarrolloweb.customers.api.dto.CustomerRequestDto;
import gt.edu.umg.desarrolloweb.customers.entity.Customer;
import gt.edu.umg.desarrolloweb.customers.mapper.CustomerMapper;
import gt.edu.umg.desarrolloweb.customers.repository.CustomerRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Business layer: rules live here, not in the controller and not in the repository.
 *
 * Dependency Injection: Spring creates CustomerRepository and CustomerMapper and passes
 * them through the constructor. We never call "new CustomerRepository()".
 * With a single constructor, @Autowired is not required.
 */
@Service
public class CustomerService {

    private final CustomerRepository customerRepository;
    private final CustomerMapper customerMapper;

    public CustomerService(CustomerRepository customerRepository, CustomerMapper customerMapper) {
        this.customerRepository = customerRepository;
        this.customerMapper = customerMapper;
    }

    @Transactional(readOnly = true)
    public List<CustomerDto> list(Boolean active) {
        List<Customer> customers = active == null
                ? customerRepository.findAll()
                : customerRepository.findByActive(active);
        return customers.stream().map(customerMapper::toDto).toList();
    }

    @Transactional(readOnly = true)
    public CustomerDto get(Long customerId) {
        return customerMapper.toDto(findOrThrow(customerId));
    }

    @Transactional
    public CustomerDto create(CustomerRequestDto request) {
        if (customerRepository.existsByMeterCode(request.getMeterCode())) {
            throw new DuplicateMeterCodeException(request.getMeterCode());
        }
        Customer customer = new Customer();
        customerMapper.copyToEntity(request, customer);
        return customerMapper.toDto(customerRepository.save(customer));
    }

    @Transactional
    public CustomerDto update(Long customerId, CustomerRequestDto request) {
        Customer customer = findOrThrow(customerId);
        if (customerRepository.existsByMeterCodeAndIdNot(request.getMeterCode(), customerId)) {
            throw new DuplicateMeterCodeException(request.getMeterCode());
        }
        customerMapper.copyToEntity(request, customer);
        // No explicit save() needed: inside a transaction JPA detects the changes
        // ("dirty checking") and issues the UPDATE on commit. We call it anyway for clarity.
        return customerMapper.toDto(customerRepository.save(customer));
    }

    @Transactional
    public void delete(Long customerId) {
        if (!customerRepository.existsById(customerId)) {
            throw new CustomerNotFoundException(customerId);
        }
        customerRepository.deleteById(customerId);
    }

    private Customer findOrThrow(Long customerId) {
        return customerRepository.findById(customerId)
                .orElseThrow(() -> new CustomerNotFoundException(customerId));
    }
}

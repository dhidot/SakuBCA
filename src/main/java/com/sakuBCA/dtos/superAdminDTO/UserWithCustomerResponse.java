package com.sakuBCA.dtos.superAdminDTO;

import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
public class UserWithCustomerResponse {
    private UUID id;
    private String name;
    private String email;
    private String role;
    private CustomerDetailsDTO customerDetails;
}

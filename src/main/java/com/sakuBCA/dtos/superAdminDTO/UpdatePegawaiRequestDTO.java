package com.sakuBCA.dtos.superAdminDTO;

import lombok.Data;

@Data
public class UpdatePegawaiRequestDTO {
    private String name;
    private String email;
    private String nip;
    private String branchId;
    private String statusPegawai;
}
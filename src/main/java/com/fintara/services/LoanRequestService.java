package com.fintara.services;

import com.fintara.dtos.loanRequestDTO.*;
import com.fintara.enums.LoanStatusGroup;
import com.fintara.exceptions.CustomException;
import com.fintara.models.*;
import com.fintara.repositories.InterestPerTenorRepository;
import com.fintara.repositories.LoanRequestRepository;
import com.fintara.repositories.PlafondRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class LoanRequestService {
    @Autowired
    private LoanRequestRepository loanRequestRepository;

    @Autowired
    private LoanStatusService loanStatusService;

    @Autowired
    private CustomerDetailsService customerDetailsService;

    @Autowired
    private BranchService branchService;

    @Autowired
    private UserService userService;

    @Autowired
    @Lazy
    private LoanApprovalService loanApprovalService;

    @Autowired
    private RepaymentScheduleService repaymentScheduleService;

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private InterestPerTenorRepository interestPerTenorRepository;

    @Autowired
    private PlafondService plafondService;

    @Autowired
    private EmailService emailService;

    private User getAuthenticatedUser() {
        UserDetails userDetails = (UserDetails) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        String username = userDetails.getUsername();
        return userService.findByEmail(username);
    }

    // Get loan request byID
    public LoanRequest findById(UUID id) {
        return loanRequestRepository.findById(id)
                .orElseThrow(() -> new CustomException("Loan request tidak ditemukan!", HttpStatus.NOT_FOUND));
    }

    // Save loan request
    public LoanRequest saveLoanRequest(LoanRequest loanRequest) {
        try {
            return loanRequestRepository.save(loanRequest);
        } catch (Exception e) {
            throw new CustomException("Gagal menyimpan loan request", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    private void validateCustomerProfile(CustomerDetails customerDetails) {
        List<String> missingFields = new ArrayList<>();

        if (customerDetails.getJenisKelamin() == null) {
            missingFields.add("Jenis Kelamin");
        }
        if (customerDetails.getTtl() == null) {
            missingFields.add("Tanggal Lahir");
        }
        if (customerDetails.getKtpUrl() == null || customerDetails.getKtpUrl().isBlank()) {
            missingFields.add("KTP");
        }
        if (customerDetails.getSelfieKtpUrl() == null || customerDetails.getSelfieKtpUrl().isBlank()) {
            missingFields.add("Selfie KTP");
        }
        if (customerDetails.getAlamat() == null || customerDetails.getAlamat().isBlank()) {
            missingFields.add("Alamat");
        }
        if (customerDetails.getNoTelp() == null || customerDetails.getNoTelp().isBlank()) {
            missingFields.add("Nomor Telepon");
        }
        if (customerDetails.getNik() == null || customerDetails.getNik().isBlank()) {
            missingFields.add("NIK");
        }
        if (customerDetails.getNamaIbuKandung() == null || customerDetails.getNamaIbuKandung().isBlank()) {
            missingFields.add("Nama Ibu Kandung");
        }
        if (customerDetails.getPekerjaan() == null || customerDetails.getPekerjaan().isBlank()) {
            missingFields.add("Pekerjaan");
        }
        if (customerDetails.getGaji() == null) {
            missingFields.add("Gaji");
        }
        if (customerDetails.getNoRek() == null || customerDetails.getNoRek().isBlank()) {
            missingFields.add("Nomor Rekening");
        }
        if (customerDetails.getStatusRumah() == null || customerDetails.getStatusRumah().isBlank()) {
            missingFields.add("Status Rumah");
        }

        if (!missingFields.isEmpty()) {
            throw new CustomException(
                    "Data wajib diisi belum lengkap",
                    HttpStatus.BAD_REQUEST,
                    missingFields
            );
        }
    }

    /**
     * Melakukan simulasi pinjaman publik dengan pemilihan plafond.
     *
     * @param request Data permintaan simulasi pinjaman yang berisi
     *                jumlah dan tenor pinjaman.
     *                @return LoanPreviewResponseDTO yang berisi estimasi pinjaman.
     *                @throws CustomException jika ada kesalahan dalam proses simulasi pinjaman.
     */
    public LoanPreviewResponseDTO simulateWebLoan(LoanSimulationWebRequestDTO request) {
        // Ambil plafond sesuai pilihan user
        Plafond selectedPlafond = plafondService.getPlafondByName(request.getPlafondName());

        if (selectedPlafond == null) {
            throw new CustomException("Plafond dengan nama " + request.getPlafondName() + " tidak ditemukan.", HttpStatus.BAD_REQUEST);
        }

        // Validasi amount dan tenor
        if (request.getAmount().compareTo(selectedPlafond.getMaxAmount()) > 0) {
            throw new CustomException("Jumlah pinjaman melebihi batas maksimum plafond " + request.getPlafondName(), HttpStatus.BAD_REQUEST);
        }

        if (request.getTenor() > selectedPlafond.getMaxTenor()) {
            throw new CustomException("Tenor melebihi batas maksimum plafond " + request.getPlafondName(), HttpStatus.BAD_REQUEST);
        }

        // Cari bunga sesuai plafond dan tenor
        Optional<InterestPerTenor> interestOpt = interestPerTenorRepository
                .findByPlafondAndTenor(selectedPlafond, request.getTenor())
                .stream().findFirst();

        if (interestOpt.isEmpty()) {
            throw new CustomException("Interest rate untuk tenor " + request.getTenor() + " pada plafond " + request.getPlafondName() + " tidak ditemukan.", HttpStatus.BAD_REQUEST);
        }

        BigDecimal interestRate = interestOpt.get().getInterestRate();
        BigDecimal feesAmount = request.getAmount().multiply(selectedPlafond.getFeeRate());
        BigDecimal disbursedAmount = request.getAmount().subtract(feesAmount);
        BigDecimal interestAmount = request.getAmount().multiply(interestRate);
        BigDecimal totalRepayment = request.getAmount().add(interestAmount).add(feesAmount);
        BigDecimal estimatedInstallment = totalRepayment.divide(BigDecimal.valueOf(request.getTenor()), 0, RoundingMode.CEILING);

        return LoanPreviewResponseDTO.builder()
                .requestedAmount(request.getAmount())
                .disbursedAmount(disbursedAmount)
                .tenor(request.getTenor())
                .interestRate(interestRate)
                .interestAmount(interestAmount)
                .feesAmount(feesAmount)
                .totalRepayment(totalRepayment)
                .estimatedInstallment(estimatedInstallment)
                .build();
    }

    /**
     * Melakukan simulasi pinjaman publik dengan plafon default.
     *
     * @param request Data permintaan simulasi pinjaman yang berisi
     *                jumlah dan tenor pinjaman.
     *                @return LoanPreviewResponseDTO yang berisi estimasi pinjaman.
     *                @throws CustomException jika ada kesalahan dalam proses simulasi pinjaman.
     */
    public LoanPreviewResponseDTO simulatePublicLoan(LoanSimulationRequestDTO request) {
        // Gunakan plafon default, misal Bronze
        Plafond defaultPlafond = plafondService.getPlafondByName("Bronze");

        Optional<InterestPerTenor> interestOpt = interestPerTenorRepository
                .findByPlafondAndTenor(defaultPlafond, request.getTenor())
                .stream().findFirst();

        if (interestOpt.isEmpty()) {
            throw new CustomException("Interest rate untuk tenor " + request.getTenor() + " pada plafond default tidak ditemukan.", HttpStatus.BAD_REQUEST);
        }

        BigDecimal interestRate = interestOpt.get().getInterestRate();
        BigDecimal feesAmount = request.getAmount().multiply(defaultPlafond.getFeeRate());
        BigDecimal disbursedAmount = request.getAmount().subtract(feesAmount);
        BigDecimal interestAmount = request.getAmount().multiply(interestRate);
        BigDecimal totalRepayment = request.getAmount().add(interestAmount).add(feesAmount);
        BigDecimal estimatedInstallment = totalRepayment.divide(BigDecimal.valueOf(request.getTenor()), 0, RoundingMode.CEILING);

        return LoanPreviewResponseDTO.builder()
                .requestedAmount(request.getAmount())
                .disbursedAmount(disbursedAmount)
                .tenor(request.getTenor())
                .interestRate(interestRate)
                .interestAmount(interestAmount)
                .feesAmount(feesAmount)
                .totalRepayment(totalRepayment)
                .estimatedInstallment(estimatedInstallment)
                .build();
    }

    /**
     * Melakukan pratinjau pengajuan pinjaman berdasarkan data yang diberikan.
     *
     * @param requestDTO Data pengajuan pinjaman yang berisi jumlah dan tenor.
     * @return LoanPreviewResponseDTO yang berisi estimasi pinjaman.
     * @throws CustomException jika ada kesalahan dalam proses pratinjau pinjaman.
     */
    public LoanPreviewResponseDTO previewLoanRequest(LoanRequestDTO requestDTO) {
        User currentUser = userService.getAuthenticatedUser();

        validateCustomer(currentUser);

        CustomerDetails customerDetails = currentUser.getCustomerDetails();
        if (customerDetails == null) {
            throw new CustomException("Customer details tidak ditemukan", HttpStatus.NOT_FOUND);
        }

        validateCustomer(currentUser);

        Plafond customerPlafond = validatePlafond(customerDetails, requestDTO.getAmount(), requestDTO.getTenor());

        Optional<InterestPerTenor> interestOpt = interestPerTenorRepository
                .findByPlafondAndTenor(customerPlafond, requestDTO.getTenor())
                .stream().findFirst();

        if (interestOpt.isEmpty()) {
            throw new CustomException("Interest rate tidak ditemukan", HttpStatus.BAD_REQUEST);
        }

        // Ambil disbursedAmount setelah dikurangi biaya admin (fees)
        // Hitung bunga = amount * interestRate
        BigDecimal disbursedAmount = requestDTO.getAmount().subtract(requestDTO.getAmount().multiply(customerPlafond.getFeeRate()));
        BigDecimal interestRate = interestOpt.get().getInterestRate();
        BigDecimal interestAmount = requestDTO.getAmount().multiply(interestRate);
        //fee amount diambila dari fee_rate di plafond sesuai dengan plafond customer
        BigDecimal feesAmount = requestDTO.getAmount().multiply(customerPlafond.getFeeRate());

        BigDecimal totalRepayment = requestDTO.getAmount().add(interestAmount).add(feesAmount);

        // Hitung estimasi cicilan = totalRepayment / tenor
        BigDecimal estimatedInstallment = totalRepayment.divide(BigDecimal.valueOf(requestDTO.getTenor()), 0, RoundingMode.CEILING);

        return LoanPreviewResponseDTO.builder()
                .requestedAmount(requestDTO.getAmount())
                .disbursedAmount(disbursedAmount)
                .tenor(requestDTO.getTenor())
                .interestRate(interestRate)
                .interestAmount(interestAmount)
                .feesAmount(feesAmount)
                .totalRepayment(totalRepayment)
                .estimatedInstallment(estimatedInstallment)
                .build();
    }

    // buat comment yang lengkap tapi pakai bahasa Indonesia
    /**
     * Membuat pengajuan pinjaman baru.
     *
     * @param requestDTO Data pengajuan pinjaman yang berisi jumlah, tenor, dan lokasi.
     * @return LoanRequestResponseDTO yang berisi detail pengajuan pinjaman yang telah dibuat.
     * @throws CustomException jika ada kesalahan dalam proses pembuatan pengajuan pinjaman.
     */
    @Transactional
    public LoanRequestResponseDTO createLoanRequest(LoanRequestDTO requestDTO) {
        User currentUser = userService.getAuthenticatedUser();

        CustomerDetails customerDetails = currentUser.getCustomerDetails();
        if (customerDetails == null) {
            throw new CustomException("Customer details tidak ditemukan", HttpStatus.NOT_FOUND);
        }

        validateCustomer(currentUser);

        List<String> pendingStatuses = List.of("REVIEW", "DIREKOMENDASIKAN_MARKETING", "DISETUJUI_BM");
        boolean hasPendingRequest = loanRequestRepository.existsByCustomerAndStatus_NameIn(customerDetails, pendingStatuses);
        if (hasPendingRequest) {
            throw new CustomException("Anda masih memiliki pengajuan yang sedang diproses. Harap tunggu hingga pengajuan sebelumnya selesai.", HttpStatus.BAD_REQUEST);
        }

        Plafond customerPlafond = validatePlafond(customerDetails, requestDTO.getAmount(), requestDTO.getTenor());

        Optional<InterestPerTenor> interestOpt = interestPerTenorRepository.findByPlafondAndTenor(customerPlafond, requestDTO.getTenor())
                .stream().findFirst();

        if (interestOpt.isEmpty()) {
            throw new CustomException("Interest rate untuk tenor " + requestDTO.getTenor() + " pada plafond "
                    + customerPlafond.getName() + " tidak ditemukan.", HttpStatus.BAD_REQUEST);
        }

        BigDecimal interestRate = interestOpt.get().getInterestRate();

        BigDecimal interestAmount = requestDTO.getAmount().multiply(interestRate);

        BigDecimal feesAmount = requestDTO.getAmount().multiply(customerPlafond.getFeeRate());

        UUID branchId = branchService.findNearestBranchWithMarketing(requestDTO.getLatitude(), requestDTO.getLongitude());
        if (branchId == null) {
            throw new CustomException("Tidak ada cabang terdekat", HttpStatus.BAD_REQUEST);
        }

        User assignedMarketing = assignMarketing(branchId);

        LoanStatus pendingStatus = loanStatusService.findByName("REVIEW");

        LoanRequest newRequest = LoanRequest.builder()
                .customer(customerDetails)
                .amount(requestDTO.getAmount())
                .tenor(requestDTO.getTenor())
                .branch(branchService.findBranchById(branchId))
                .marketing(assignedMarketing)
                .requestDate(LocalDateTime.now())
                .status(pendingStatus)
                .plafond(customerPlafond)
                .interestRate(interestRate)
                .interestAmount(interestAmount)
                .feesAmount(feesAmount)
                .build();

        LoanRequest savedLoanRequest = loanRequestRepository.save(newRequest);

        return LoanRequestResponseDTO.fromEntity(savedLoanRequest);
    }


    private Plafond validatePlafond(CustomerDetails customerDetails, BigDecimal amount, int tenor) {
        Plafond plafond = customerDetails.getPlafond();

        // Validasi remainingPlafond
        if (amount.compareTo(customerDetails.getRemainingPlafond()) > 0) {
            throw new CustomException("Sisa plafond tidak mencukupi!", HttpStatus.BAD_REQUEST);
        }

        // Validasi tenor berdasarkan data InterestPerTenor
        boolean tenorAvailable = interestPerTenorRepository.existsByPlafondAndTenor(plafond, tenor);
        if (!tenorAvailable) {
            throw new CustomException("Tenor tidak tersedia untuk paket plafond ini!", HttpStatus.BAD_REQUEST);
        }

        return plafond;
    }

    private void validateCustomer(User user) {
        if (!user.getRole().getName().equals("CUSTOMER")) {
            throw new CustomException("Hanya customer yang bisa membuat loan request", HttpStatus.FORBIDDEN);
        }

        CustomerDetails customerDetails = user.getCustomerDetails();
        if (customerDetails == null) {
            throw new CustomException("Data customer belum ditemukan. Harap lengkapi profil terlebih dahulu", HttpStatus.BAD_REQUEST);
        }

        if (isNullOrEmpty(customerDetails.getNik()) ||
                isNullOrEmpty(customerDetails.getAlamat()) ||
                isNullOrEmpty(customerDetails.getNoTelp()) ||
                isNullOrEmpty(customerDetails.getNamaIbuKandung()) ||
                isNullOrEmpty(customerDetails.getPekerjaan()) ||
                customerDetails.getGaji() == null ||
                isNullOrEmpty(customerDetails.getNoRek()) ||
                customerDetails.getStatusRumah() == null ||
                isNullOrEmpty(customerDetails.getKtpUrl()) ||
                isNullOrEmpty(customerDetails.getSelfieKtpUrl())
            )
        {

            throw new CustomException("Data customer belum lengkap. Harap lengkapi profil dan dokumen pribadi sebelum mengajukan pinjaman", HttpStatus.BAD_REQUEST);
        }
    }

    private boolean isNullOrEmpty(String value) {
        return value == null || value.trim().isEmpty();
    }

    private User assignMarketing(UUID branchId) {
        List<User> marketingByBranch = userService.getMarketingByBranch(branchId);

        if (marketingByBranch.isEmpty()) {
            throw new CustomException("Tidak ada marketing yang tersedia di cabang ini", HttpStatus.BAD_REQUEST);
        }

        // Ambil jumlah loan request yang sedang ditangani setiap marketing
        List<Object[]> rawCounts = loanRequestRepository.countLoanRequestsByMarketing(branchId);
        Map<UUID, Long> marketingLoad = new HashMap<>();

        for (Object[] row : rawCounts) {
            UUID marketingId = (UUID) row[0];
            Long count = (Long) row[1];
            marketingLoad.put(marketingId, count);
        }

        // Pilih marketing dengan jumlah tugas paling sedikit
        return marketingByBranch.stream()
                .min(Comparator.comparing(marketing -> marketingLoad.getOrDefault(marketing.getId(), 0L)))
                .orElse(marketingByBranch.get(0)); // Jika semua marketing punya jumlah tugas yang sama, pilih yang pertama
    }

    public void validateAccess(User currentUser, LoanRequest loanRequest) {
        // Ambil status dari LoanRequest, status merupakan entitas LoanStatus
        LoanStatus loanStatus = loanRequest.getStatus();

        // Ambil nama status dari LoanStatus
        String status = loanStatus.getName();

        switch (status) {
            case "REVIEW" -> {
                // Cek jika user adalah marketing yang bersangkutan
                if (!currentUser.getId().equals(loanRequest.getMarketing().getId())) {
                    throw new CustomException("Hanya marketing terkait yang bisa mengakses.", HttpStatus.FORBIDDEN);
                }
            }
            case "DIREKOMENDASIKAN_MARKETING" -> {
                // Cek jika user adalah Branch Manager di cabang yang sesuai
                if (!currentUser.isBranchManager() || !currentUser.getPegawaiDetails().getBranch().getId().equals(loanRequest.getBranch().getId())) {
                    throw new CustomException("Hanya BM di cabang ini yang bisa mengakses.", HttpStatus.FORBIDDEN);
                }
            }
            case "DISETUJUI_BM" -> {
                // Cek jika user adalah Back Office di cabang yang sesuai
                if (!currentUser.isBackOffice() || !currentUser.getPegawaiDetails().getBranch().getId().equals(loanRequest.getBranch().getId())) {
                    throw new CustomException("Hanya Back Office di cabang ini yang bisa mengakses.", HttpStatus.FORBIDDEN);
                }
            }
            default -> throw new CustomException("Status tidak dikenali atau akses tidak diizinkan.", HttpStatus.FORBIDDEN);
        }
    }

    public LoanRequest getLoanRequestById(UUID id) {
        Optional<LoanRequest> loanRequestOptional = loanRequestRepository.findById(id);
        if (loanRequestOptional.isPresent()) {
            return loanRequestOptional.get();
        } else {
            throw new CustomException("LoanRequest not found", HttpStatus.NOT_FOUND);
        }
    }

    public LoanRequestApprovalDTO mapToApprovalDTO(LoanRequest loanRequest) {
        CustomerDetails customer = loanRequest.getCustomer();

        // ambil semua LoanApproval untuk request ini
        List<LoanApproval> approvals = loanApprovalService.findByLoanRequestId(loanRequest.getId());

        // catatan marketing: status = DIREKOMENDASIKAN_MARKETING atau DITOLAK_MARKETING
        String marketingNotes = approvals.stream()
                .filter(a -> "DIREKOMENDASIKAN_MARKETING".equals(a.getStatus().getName())
                        || "DITOLAK_MARKETING".equals(a.getStatus().getName()))
                .map(LoanApproval::getNotes)
                .findFirst()
                .orElse(null);

        // catatan BM: status = DISETUJUI_BM atau DITOLAK_BM
        String bmNotes = approvals.stream()
                .filter(a -> "DISETUJUI_BM".equals(a.getStatus().getName())
                        || "DITOLAK_BM".equals(a.getStatus().getName()))
                .map(LoanApproval::getNotes)
                .findFirst()
                .orElse(null);

        String backOfficeNotes = approvals.stream()
                .filter(a -> "DISBURSED".equals(a.getStatus().getName()))
                .map(LoanApproval::getNotes)
                .findFirst()
                .orElse(null);

        return LoanRequestApprovalDTO.builder()
                .id(loanRequest.getId())
                .customerJob(customer.getPekerjaan())
                .customerSalary(customer.getGaji())
                .status(loanRequest.getStatus().getName())
                .amount(loanRequest.getAmount())
                .tenor(loanRequest.getTenor())
                .requestDate(loanRequest.getRequestDate())

                .customerName(customer.getUser().getName())
                .customerKtpPhotoUrl(customer.getKtpUrl())
                .customerSelfieKtpPhotoUrl(customer.getSelfieKtpUrl())
                .customerEmail(customer.getUser().getEmail())
                .customerPhone(customer.getNoTelp())
                .customerAddress(customer.getAlamat())

                .marketingNotes(marketingNotes)
                .bmNotes(bmNotes)
                .backOfficeNotes(backOfficeNotes)
                .build();
    }

    /********** MARKETING APPROVAL **********/
    private LoanRequestApprovalDTO convertToDTO(LoanRequest loanRequest) {
        CustomerDetails customer = loanRequest.getCustomer();
        User user = customer.getUser();

        return LoanRequestApprovalDTO.builder()
                .id(loanRequest.getId())
                .customerName(user.getName())
                .customerEmail(user.getEmail())
                .customerPhone(customer.getNoTelp())
                .customerAddress(customer.getAlamat())
                .customerJob(customer.getPekerjaan())
                .customerSalary(customer.getGaji())
                .customerKtpPhotoUrl(customer.getKtpUrl())
                .customerSelfieKtpPhotoUrl(customer.getSelfieKtpUrl())
                .amount(loanRequest.getAmount())
                .tenor(loanRequest.getTenor())
                .status(loanRequest.getStatus().getName())
                .requestDate(loanRequest.getRequestDate())
                .build();
    }


    public List<LoanRequestApprovalDTO> getLoanRequestsByMarketing(UUID marketingId) {
        List<LoanRequest> loanRequests = loanRequestRepository.findByMarketingId(marketingId);

        return loanRequests.stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    @Transactional
    public void reviewLoanRequest(UUID loanRequestId, UUID marketingId, String status, String notes, String notesIdentitas, String notesPlafond, String notesSummary) {
        // 1️⃣ Ambil loan request yang bersangkutan
        LoanRequest loanRequest = loanRequestRepository.findById(loanRequestId)
                .orElseThrow(() -> new CustomException("Loan request tidak ditemukan", HttpStatus.NOT_FOUND));

        // 2️⃣ Cek apakah marketing yang login sesuai dengan yang menangani request ini
        if (!loanRequest.getMarketing().getId().equals(marketingId)) {
            throw new CustomException("Anda tidak berhak mereview loan request ini", HttpStatus.FORBIDDEN);
        }

        System.out.println("Loan Request ID: " + loanRequest.getId());
        System.out.println("Notes: " + notes);
        System.out.println("Notes Identitas: " + notesIdentitas);
        System.out.println("Notes Plafond: " + notesPlafond);
        System.out.println("Notes Summary: " + notesSummary);

        // 3️⃣ Update status berdasarkan hasil review
        LoanStatus loanStatus = loanStatusService.findByName(status); // Status sudah diterima sebagai parameter
        loanRequest.setStatus(loanStatus);
        if ("DIREKOMENDASIKAN_MARKETING".equals(status)) {
            loanRequest.setApprovalMarketingAt(LocalDateTime.now());
        }

        // Simpan perubahan loan request
        loanRequestRepository.save(loanRequest);

        // 4️⃣ Simpan review di LoanApproval
        LoanApproval loanApproval = LoanApproval.builder()
                .loanRequest(loanRequest)
                .handledBy(loanRequest.getMarketing()) // Marketing yang mereview
                .status(loanStatus)
                .notes(notes) // Bisa jadi alasan penolakan atau catatan lain
                .notesIdentitas(notesIdentitas)// Catatan identitas
                .notesPlafond(notesPlafond) // Catatan plafon
                .notesSummary(notesSummary) // Catatan ringkasan
                .approvedAt(LocalDateTime.now())
                .build();

        loanApprovalService.save(loanApproval);
    }


    /********** BM APPROVAL **********/
    public List<LoanRequestApprovalDTO> getLoanRequestsForBranchManager(UUID branchManagerId) {
        // 1️⃣ Ambil branch ID dari branch manager
        UUID branchId = userService.getBranchIdByUserId(branchManagerId);

        // 2️⃣ Ambil semua loan request di branch ini dengan status "APPROVED_MARKETING"
        List<LoanRequest> loanRequests = loanRequestRepository.findByBranchIdAndStatus(branchId, "DIREKOMENDASIKAN_MARKETING");

        // 3️⃣ Konversi ke DTO agar response lebih rapi
        return loanRequests.stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    @Transactional
    public void reviewLoanRequestByBM(UUID loanRequestId, UUID branchManagerId, String status, String notes, String notesIdentitas, String notesPlafond, String notesSummary) {
        // 1️⃣ Ambil loan request
        LoanRequest loanRequest = loanRequestRepository.findById(loanRequestId)
                .orElseThrow(() -> new CustomException("Loan request tidak ditemukan", HttpStatus.NOT_FOUND));

        // 2️⃣ Cek apakah BM berhak mereview (harus di branch yang sama)
        UUID branchId = userService.getBranchIdByUserId(branchManagerId);
        if (!loanRequest.getBranch().getId().equals(branchId)) {
            throw new CustomException("Anda tidak berhak mereview loan request ini", HttpStatus.FORBIDDEN);
        }

        // 3️⃣ Update status berdasarkan review BM
        LoanStatus newStatus = loanStatusService.findByName(status); // Status sudah diterima sebagai parameter
        loanRequest.setStatus(newStatus);
        loanRequest.setApprovalBMAt(LocalDateTime.now()); // Timestamp approval BM

        // print semua notes, notesIdentitas, notesPlafond, notesSummary dalam satu baris
        // Catatan ringkasan
        System.out.println("Loan Request ID: " + loanRequest.getId());
        System.out.println("Notes: " + notes);
        System.out.println("Notes Identitas: " + notesIdentitas);
        System.out.println("Notes Plafond: " + notesPlafond);
        System.out.println("Notes Summary: " + notesSummary);


        // Simpan perubahan loan request
        loanRequestRepository.save(loanRequest);

        // 4️⃣ Simpan history approval BM di loan_approvals
        LoanApproval approvalRecord = LoanApproval.builder()
                .loanRequest(loanRequest)
                .handledBy(userService.findById(branchManagerId)) // BM yang approve
                .status(newStatus)
                .notes(notes)
                .notesIdentitas(notesIdentitas)// Catatan identitas
                .notesPlafond(notesPlafond) // Catatan plafon
                .notesSummary(notesSummary) // Catatan ringkasan
                .approvedAt(LocalDateTime.now())
                .build();
        loanApprovalService.save(approvalRecord);

        // 5️⃣ Kirim notifikasi jika status approved
        if ("DISETUJUI_BM".equalsIgnoreCase(newStatus.getName())) {
            User applicant = loanRequest.getCustomer().getUser();
            String title = "Pengajuan Pinjaman Disetujui";
            String body = "Pengajuan pinjaman Anda telah disetujui oleh Branch Manager.";
            notificationService.sendNotificationToUser(applicant.getId(), title, body);
        }
    }


    /********* BACK OFFICE DISBURSE **********/
    public List<LoanRequestApprovalDTO> getLoanRequestsForBackOffice(UUID backOfficeId) {
        // 1️⃣ Ambil branch ID dari Back Office yang login
        UUID branchId = userService.getBranchIdByUserId(backOfficeId);

        // 2️⃣ Ambil semua loan request dengan status "DISETUJUI_BM" di branch ini
        List<LoanRequest> loanRequests = loanRequestRepository.findByBranchIdAndStatus(branchId, "DISETUJUI_BM");

        // 3️⃣ Konversi ke DTO agar response lebih rapi
        return loanRequests.stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    @Transactional
    public void disburseLoanRequest(UUID loanRequestId, UUID backOfficeId, String status, String notes, String notesIdentitas, String notesPlafond, String notesSummary) {
        LoanRequest loanRequest = findAndValidateLoanRequest(loanRequestId, backOfficeId);
        LoanStatus targetStatus = loanStatusService.findByName(status);

        updateLoanRequestStatusAndCalculation(loanRequest, targetStatus);

        saveApprovalRecord(loanRequest, backOfficeId, targetStatus, notes, notesIdentitas, notesPlafond, notesSummary);

        if ("DISBURSED".equalsIgnoreCase(status)) {
            handleDisbursement(loanRequest);
        } else {
            handleNonDisbursement(loanRequest);
        }
    }

    private LoanRequest findAndValidateLoanRequest(UUID loanRequestId, UUID backOfficeId) {
        LoanRequest loanRequest = loanRequestRepository.findById(loanRequestId)
                .orElseThrow(() -> new CustomException("Loan request tidak ditemukan", HttpStatus.NOT_FOUND));

        if (!"DISETUJUI_BM".equalsIgnoreCase(loanRequest.getStatus().getName())) {
            throw new CustomException("Loan request belum siap untuk dicairkan", HttpStatus.BAD_REQUEST);
        }

        UUID branchId = userService.getBranchIdByUserId(backOfficeId);
        if (!loanRequest.getBranch().getId().equals(branchId)) {
            throw new CustomException("Anda tidak berhak mencairkan loan request ini", HttpStatus.FORBIDDEN);
        }

        return loanRequest;
    }

    private void updateLoanRequestStatusAndCalculation(LoanRequest loanRequest, LoanStatus targetStatus) {
        loanRequest.setStatus(targetStatus);
        loanRequest.setDisbursedAt(LocalDateTime.now());

        BigDecimal amount = loanRequest.getAmount();
        int tenor = loanRequest.getTenor();
        BigDecimal interestRate = loanRequest.getInterestRate();
        BigDecimal feeRate = loanRequest.getPlafond().getFeeRate();

        BigDecimal interestAmount = amount.multiply(interestRate).multiply(BigDecimal.valueOf(tenor));
        BigDecimal feesAmount = amount.multiply(feeRate);
        BigDecimal disbursedAmount = amount.subtract(feesAmount);
        BigDecimal totalRepayment = amount.add(interestAmount);
        BigDecimal estimatedInstallment = totalRepayment.divide(BigDecimal.valueOf(tenor), 0, RoundingMode.CEILING);

        loanRequest.setInterestAmount(interestAmount);
        loanRequest.setFeesAmount(feesAmount);
        loanRequest.setDisbursedAmount(disbursedAmount);
        loanRequest.setTotalRepaymentAmount(totalRepayment);
        loanRequest.setEstimatedInstallment(estimatedInstallment);
    }

    private void saveApprovalRecord(LoanRequest loanRequest, UUID backOfficeId, LoanStatus targetStatus, String notes, String notesIdentitas, String notesPlafond, String notesSummary) {
        LoanApproval approval = LoanApproval.builder()
                .loanRequest(loanRequest)
                .handledBy(userService.findById(backOfficeId))
                .status(targetStatus)
                .notes(notes)
                .notesIdentitas(notesIdentitas)
                .notesPlafond(notesPlafond)
                .notesSummary(notesSummary)
                .approvedAt(LocalDateTime.now())
                .build();

        loanApprovalService.save(approval);
    }

    private void handleDisbursement(LoanRequest loanRequest) {
        CustomerDetails customer = loanRequest.getCustomer();
        BigDecimal amount = loanRequest.getAmount();
        BigDecimal remainingPlafond = customer.getRemainingPlafond();

        if (remainingPlafond.compareTo(amount) < 0) {
            throw new CustomException("Plafon tidak mencukupi untuk disbursement", HttpStatus.BAD_REQUEST);
        }

        customer.setRemainingPlafond(remainingPlafond.subtract(amount));
        customerDetailsService.saveCustomerDetails(customer);

        loanRequestRepository.save(loanRequest);

        repaymentScheduleService.generateRepaymentSchedulesForLoan(loanRequest);

        User applicant = customer.getUser();
        String title = "Pinjaman Dicairkan";
        String body = "Pinjaman Anda telah dicairkan dengan nominal sebesar " + loanRequest.getDisbursedAmount() + ".";
        notificationService.sendNotificationToUser(applicant.getId(), title, body);

        emailService.sendLoanDisbursementEmail(applicant.getEmail(), applicant.getName(), loanRequest.getDisbursedAmount().toString());
    }

    private void handleNonDisbursement(LoanRequest loanRequest) {
        loanRequestRepository.save(loanRequest);

        User applicant = loanRequest.getCustomer().getUser();
        String title = "Pengajuan Pinjaman Tidak Dicairkan";
        String body = "Pengajuan pinjaman Anda tidak dapat dicairkan. Silakan hubungi customer service untuk informasi lebih lanjut.";
        notificationService.sendNotificationToUser(applicant.getId(), title, body);

        emailService.sendLoanDisbursementFailureEmail(applicant.getEmail(), applicant.getName(), loanRequest.getAmount().toString());
    }

    // get loan requests by statuses
    public List<LoanInProgressResponseDTO> getLoanRequestByStatuses(List<String> statuses) {
        User currentUser = getAuthenticatedUser();
        List<LoanRequest> loanRequests = loanRequestRepository.findAllByCustomer_User_IdAndStatus_NameIn(currentUser.getId(), statuses);

        return loanRequests.stream()
                .map(LoanInProgressResponseDTO::fromEntity)
                .collect(Collectors.toList());
    }

    // Loan History by Filter
    public List<LoanHistoryResponseDTO> findHistoryByFilter(UUID userId, LoanStatusGroup group) {
        List<String> statusNames = switch (group) {
            case APPROVED -> List.of("DISBURSED");
            case REJECTED -> List.of("DITOLAK_BM", "DITOLAK_MARKETING");
        };

        return loanRequestRepository
                .findAllByCustomer_User_IdAndStatus_NameIn(userId, statusNames)
                .stream()
                .map(loanRequest -> LoanHistoryResponseDTO.builder()
                        .id(loanRequest.getId())
                        .amount(loanRequest.getAmount())
                        .tenor(loanRequest.getTenor())
                        .interestAmount(loanRequest.getInterestAmount())
                        .disbursedAmount(loanRequest.getDisbursedAmount())
                        .status(loanRequest.getStatus().getName())
                        .createdAt(loanRequest.getCreatedAt())
                        .build())
                .toList();
    }
}


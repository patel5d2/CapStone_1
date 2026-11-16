package com.jonathansoriano.enterprisedevgroupproject.admin;

import com.jonathansoriano.enterprisedevgroupproject.admin.dto.AdminAccessResponse;
import com.jonathansoriano.enterprisedevgroupproject.admin.dto.AdminBanRequest;
import com.jonathansoriano.enterprisedevgroupproject.admin.dto.AdminBanResponse;
import com.jonathansoriano.enterprisedevgroupproject.admin.dto.AdminLiftBanRequest;
import com.jonathansoriano.enterprisedevgroupproject.admin.dto.AdminPageResponse;
import com.jonathansoriano.enterprisedevgroupproject.admin.dto.AdminStudentResponse;
import com.jonathansoriano.enterprisedevgroupproject.admin.dto.AdminStudentUpdateRequest;
import com.jonathansoriano.enterprisedevgroupproject.admin.dto.AdminUserReportResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Account management endpoints (#113). Thin on purpose: the administrator check lives in
 * {@link AdminAccountService}, so the {@code /api/admin} prefix protects nothing by itself.
 * The caller is always the verified token; no request carries an "acting as" field.
 */
@RestController
@RequestMapping("/api/admin")
public class AdminAccountController extends AdminEndpoints {

    private final AdminAccountService service;

    public AdminAccountController(AdminAccountService service) {
        this.service = service;
    }

    /** The one route here any signed-in account may call: it reports their own capability. */
    @GetMapping("/access")
    public AdminAccessResponse access(@AuthenticationPrincipal Jwt clerkSession) {
        return service.access(clerkSession);
    }

    @GetMapping("/students")
    public AdminPageResponse<AdminStudentResponse> search(@AuthenticationPrincipal Jwt clerkSession,
                                                          @RequestParam(defaultValue = "") String query,
                                                          @RequestParam(defaultValue = "0") int page,
                                                          @RequestParam(defaultValue = "20") int size) {
        return service.search(clerkSession, query, page, size);
    }

    @GetMapping("/students/{id}")
    public AdminStudentResponse get(@AuthenticationPrincipal Jwt clerkSession, @PathVariable long id) {
        return service.get(clerkSession, id);
    }

    @PutMapping("/students/{id}")
    public AdminStudentResponse update(@AuthenticationPrincipal Jwt clerkSession, @PathVariable long id,
                                       @Valid @RequestBody AdminStudentUpdateRequest request) {
        return service.update(clerkSession, id, request);
    }

    /** The body is optional: with none, the account is banned and its content is left alone. */
    @PostMapping("/students/{id}/ban")
    public AdminStudentResponse ban(@AuthenticationPrincipal Jwt clerkSession, @PathVariable long id,
                                    @RequestBody(required = false) AdminBanRequest request) {
        return service.ban(clerkSession, id, request != null && request.removeContent());
    }

    @DeleteMapping("/students/{id}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal Jwt clerkSession, @PathVariable long id,
                                       @RequestParam(defaultValue = "false") boolean ban,
                                       @RequestParam(defaultValue = "false") boolean removeContent) {
        service.delete(clerkSession, id, ban, removeContent);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/bans")
    public AdminPageResponse<AdminBanResponse> bans(@AuthenticationPrincipal Jwt clerkSession,
                                                    @RequestParam(defaultValue = "0") int page,
                                                    @RequestParam(defaultValue = "20") int size) {
        return service.bans(clerkSession, page, size);
    }

    @PostMapping("/bans/lift")
    public ResponseEntity<Void> liftBan(@AuthenticationPrincipal Jwt clerkSession,
                                        @Valid @RequestBody AdminLiftBanRequest request) {
        service.liftBan(clerkSession, request.email());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/user-reports")
    public AdminPageResponse<AdminUserReportResponse> userReports(@AuthenticationPrincipal Jwt clerkSession,
                                                                  @RequestParam(defaultValue = "0") int page,
                                                                  @RequestParam(defaultValue = "20") int size) {
        return service.userReports(clerkSession, page, size);
    }

    @DeleteMapping("/user-reports/{id}")
    public ResponseEntity<Void> dismissUserReport(@AuthenticationPrincipal Jwt clerkSession, @PathVariable long id) {
        service.dismissUserReport(clerkSession, id);
        return ResponseEntity.noContent().build();
    }
}

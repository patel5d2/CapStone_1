package com.jonathansoriano.enterprisedevgroupproject.admin;

import com.jonathansoriano.enterprisedevgroupproject.admin.dto.AdminAccessResponse;
import com.jonathansoriano.enterprisedevgroupproject.admin.dto.AdminBanResponse;
import com.jonathansoriano.enterprisedevgroupproject.admin.dto.AdminPageResponse;
import com.jonathansoriano.enterprisedevgroupproject.admin.dto.AdminStudentResponse;
import com.jonathansoriano.enterprisedevgroupproject.admin.dto.AdminStudentUpdateRequest;
import com.jonathansoriano.enterprisedevgroupproject.admin.dto.AdminUserReportResponse;
import com.jonathansoriano.enterprisedevgroupproject.domain.UserRequest;
import com.jonathansoriano.enterprisedevgroupproject.dto.StudentUpdateDto;
import com.jonathansoriano.enterprisedevgroupproject.dto.UserDto;
import com.jonathansoriano.enterprisedevgroupproject.exception.SearchNotFoundException;
import com.jonathansoriano.enterprisedevgroupproject.messages.UserReport;
import com.jonathansoriano.enterprisedevgroupproject.messages.UserReportRepository;
import com.jonathansoriano.enterprisedevgroupproject.repository.StudentRepository;
import com.jonathansoriano.enterprisedevgroupproject.repository.UserRepository;
import com.jonathansoriano.enterprisedevgroupproject.school.School;
import com.jonathansoriano.enterprisedevgroupproject.school.SchoolRepository;
import com.jonathansoriano.enterprisedevgroupproject.security.AccountBans;
import com.jonathansoriano.enterprisedevgroupproject.security.ClerkAccountDeletion;
import com.jonathansoriano.enterprisedevgroupproject.security.CurrentUser;
import com.jonathansoriano.enterprisedevgroupproject.service.DeletedAccountCleanup;
import com.jonathansoriano.enterprisedevgroupproject.webhook.ClerkIdentityRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Account management for administrators (#113): find, view, correct, ban and delete student
 * accounts.
 *
 * <p>Every method that touches another person's account begins with
 * {@link AdminAccess#require}. That first line is the authorization, so it holds for any
 * caller of this class and not only for the controller in front of it.
 *
 * <p>Nothing here acts <em>as</em> a student. The administrator's own token identifies who
 * made each change, and the log line for it carries their Clerk subject and the row id
 * (never an address, invariant 8).
 */
@Slf4j
@Service
public class AdminAccountService {

    /** Invariant 10: a list endpoint is bounded whatever the client asks for. */
    static final int MAX_PAGE_SIZE = 50;
    private static final int MAX_QUERY_LENGTH = 100;
    /** Fixed here so a client cannot name a column to sort by. The id makes the order stable. */
    private static final Sort ORDER = Sort.by("lastName", "firstName", "id");
    private static final String USER_ROLE = "USER";

    static final String NOT_FOUND_MESSAGE = "That student account no longer exists.";
    static final String ADMIN_TARGET_MESSAGE =
            "Administrator accounts cannot be banned or deleted from account management.";
    static final String NOT_BANNED_MESSAGE = "That email address is not banned.";
    static final String NO_CLERK_KEY_MESSAGE =
            "This account's sign-in could not be removed because the server has no Clerk secret key "
                    + "(CLERK_SECRET_KEY). Nothing was deleted.";
    static final String CLERK_KEY_REJECTED_MESSAGE =
            "Clerk rejected the server's secret key (CLERK_SECRET_KEY), so nothing was deleted. The key is "
                    + "mistyped, was rotated, or belongs to a different Clerk app than the one students sign in "
                    + "to. Set it to that app's secret key and restart the server.";
    static final String CLERK_UNCONFIRMED_MESSAGE =
            "Clerk did not confirm that this account's sign-in was removed, so nothing was deleted. "
                    + "Try again in a moment.";

    private final AdminAccess access;
    private final StudentAccountRepository accounts;
    private final StudentRepository students;
    private final UserRepository users;
    private final SchoolRepository schools;
    private final ClerkIdentityRepository identities;
    private final ClerkAccountDeletion clerkAccounts;
    private final DeletedAccountCleanup cleanup;
    private final AccountContentRemoval content;
    private final UserReportRepository userReports;

    public AdminAccountService(AdminAccess access, StudentAccountRepository accounts, StudentRepository students,
                               UserRepository users, SchoolRepository schools, ClerkIdentityRepository identities,
                               ClerkAccountDeletion clerkAccounts, DeletedAccountCleanup cleanup,
                               AccountContentRemoval content, UserReportRepository userReports) {
        this.access = access;
        this.accounts = accounts;
        this.students = students;
        this.users = users;
        this.schools = schools;
        this.identities = identities;
        this.clerkAccounts = clerkAccounts;
        this.cleanup = cleanup;
        this.content = content;
        this.userReports = userReports;
    }

    /** What the caller may do. Open to any signed-in account: it describes only themselves. */
    public AdminAccessResponse access(Jwt clerkSession) {
        boolean hasProfile = accounts.existsByClerkUserId(CurrentUser.subjectOf(clerkSession))
                || accounts.existsByEmail(CurrentUser.emailOf(clerkSession));
        return new AdminAccessResponse(access.isAdmin(clerkSession), hasProfile);
    }

    /**
     * One page of accounts whose name or email contains {@code query}; everything when it is
     * blank. An empty result is an empty page, not an error.
     */
    public AdminPageResponse<AdminStudentResponse> search(Jwt clerkSession, String query, int page, int size) {
        access.require(clerkSession);

        Page<StudentAccount> found = accounts.search(patternFor(query), pageRequest(page, size, ORDER));
        Map<Long, String> schoolNames = schoolNames();
        Map<String, String> roles = users.rolesOf(
                found.getContent().stream().map(StudentAccount::getEmail).toList());

        return AdminPageResponse.of(found, found.getContent().stream()
                .map(account -> toResponse(account, schoolNames, roles.get(account.getEmail())))
                .toList());
    }

    public AdminStudentResponse get(Jwt clerkSession, long studentId) {
        access.require(clerkSession);
        StudentAccount account = find(studentId);
        return toResponse(account, schoolNames(), roleOf(account.getEmail()));
    }

    /**
     * Replaces the editable fields of one account.
     *
     * <p>All of them are written by a single UPDATE statement, so a failure leaves the row as
     * it was: there is no state in which some fields changed and others did not. Everything
     * that can be refused (the role, the row, the school) is checked before that statement.
     */
    public AdminStudentResponse update(Jwt clerkSession, long studentId, AdminStudentUpdateRequest request) {
        access.require(clerkSession);
        StudentAccount current = find(studentId);

        if (schools.findById(request.getUniversityId()).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Select an available school.");
        }

        int updated = students.updateStudent(StudentUpdateDto.builder()
                .id(current.getId())
                .firstName(request.getFirstName().trim())
                .lastName(request.getLastName().trim())
                .residentCity(request.getResidentCity().trim())
                .residentState(request.getResidentState())
                .universityId(request.getUniversityId().intValue())
                .grade(request.getGrade().trim())
                .major(request.getMajor().trim())
                .socialMediaLink(blankToNull(request.getSocialMediaLink()))
                .graduationYear(request.getGraduationYear())
                .bio(blankToNull(request.getBio()))
                // Not an administrator's to change (see AdminStudentUpdateRequest): kept as stored.
                .email(current.getEmail())
                .photoUrl(current.getPhotoUrl())
                .build());
        if (updated == 0) {
            // Deleted between the read above and the write.
            throw new SearchNotFoundException(NOT_FOUND_MESSAGE);
        }

        log.info("Administrator {} updated student account {}.", CurrentUser.subjectOf(clerkSession), studentId);
        return get(clerkSession, studentId);
    }

    /**
     * Bans a student: their address is refused by every endpoint from the next request on,
     * and stays refused if they delete their account and sign up again ({@link AccountBans}).
     *
     * <p>The profile is kept, so lifting the ban puts the student back exactly as they were.
     * It is hidden from the directory while the ban stands. Banning an account that is
     * already banned changes nothing and is not an error.
     *
     * @param removeContent also remove what they posted ({@link AccountContentRemoval}).
     *                      That half is not undone by lifting the ban.
     */
    @Transactional
    public AdminStudentResponse ban(Jwt clerkSession, long studentId, boolean removeContent) {
        access.require(clerkSession);
        String admin = CurrentUser.subjectOf(clerkSession);
        StudentAccount target = find(studentId);
        refuseAdministratorTarget(target, signInsOf(target), admin);

        banEmail(target.getEmail());
        if (removeContent) {
            content.removeAllBy(target.getClerkUserId(), target.getEmail());
        }
        log.info("Administrator {} banned student account {}{}.", admin, studentId,
                removeContent ? " and removed its content" : "");
        return get(clerkSession, studentId);
    }

    /**
     * Lifts a ban. A student who still has a profile gets their ordinary account back. An
     * address with no profile left (the account was deleted and banned) has its row removed
     * altogether: sign-up refuses an address {@code app_user} still holds, so leaving a
     * {@code USER} row behind would lock that person out as surely as the ban did.
     */
    @Transactional
    public void liftBan(Jwt clerkSession, String email) {
        access.require(clerkSession);
        UserDto banned = accountRow(email)
                .filter(row -> AccountBans.BANNED_ROLE.equals(row.getRole()))
                .orElseThrow(() -> new SearchNotFoundException(NOT_BANNED_MESSAGE));

        if (accounts.existsByEmail(banned.getEmail())) {
            users.updateRole(banned.getId(), USER_ROLE);
        } else {
            users.deleteById(banned.getId());
        }
        log.info("Administrator {} lifted the ban on account row {}.", CurrentUser.subjectOf(clerkSession),
                banned.getId());
    }

    /** One page of banned addresses, each with the profile still on it if there is one. */
    public AdminPageResponse<AdminBanResponse> bans(Jwt clerkSession, int page, int size) {
        access.require(clerkSession);
        PageRequest request = pageRequest(page, size, Sort.unsorted());

        List<AdminBanResponse> items = users.findBannedEmails(request.getPageSize(), request.getOffset()).stream()
                .map(email -> accounts.findByEmail(email)
                        .map(account -> new AdminBanResponse(email, account.getId(),
                                (account.getFirstName() + " " + account.getLastName()).trim()))
                        .orElseGet(() -> new AdminBanResponse(email, null, null)))
                .toList();
        long total = users.countBanned();
        return new AdminPageResponse<>(items, request.getPageNumber(), request.getPageSize(), total,
                (int) Math.ceil((double) total / request.getPageSize()));
    }

    /**
     * Deletes a student account: its sign-in first, then its profile.
     *
     * <p><b>The order is the point.</b> The sign-in is removed from Clerk before anything is
     * deleted here, and the profile goes only once Clerk has confirmed. If Clerk cannot be
     * asked or does not confirm, the request fails and nothing has changed, so an account is
     * never reported deleted while its owner can still sign in. The other way round (profile
     * first) would leave exactly that when the Clerk call failed. If a later step fails
     * after the first succeeded, repeating the delete finishes it: Clerk answers "already
     * gone" and the rest is removed.
     *
     * <p>What is removed and what is kept is the existing rule for a deleted account, applied
     * by {@link DeletedAccountCleanup} rather than restated here: the profile and its
     * {@code app_user} row go; listings, messages and posts stay, belonging
     * to nobody — unless {@code removeContent} asks for those to go too.
     *
     * <p>A row with no Clerk account bound to it (the demo data) has no sign-in to remove,
     * unless the Clerk webhook has recorded an account on the same address, in which case
     * that one is removed.
     *
     * @param ban           also ban the address, so signing up again does not bring them
     *                      back. Written before the profile is removed: the cleanup only
     *                      takes a {@code USER} row with it, so the ban is what remains.
     * @param removeContent also remove what they posted ({@link AccountContentRemoval})
     */
    public void delete(Jwt clerkSession, long studentId, boolean ban, boolean removeContent) {
        access.require(clerkSession);
        String admin = CurrentUser.subjectOf(clerkSession);
        StudentAccount target = find(studentId);
        String boundSubject = target.getClerkUserId();
        List<String> signIns = signInsOf(target);
        refuseAdministratorTarget(target, signIns, admin);

        for (String signIn : signIns) {
            switch (clerkAccounts.delete(signIn, admin)) {
                case REMOVED -> { }
                case NOT_CONFIGURED ->
                        throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, NO_CLERK_KEY_MESSAGE);
                case KEY_REJECTED ->
                        throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, CLERK_KEY_REJECTED_MESSAGE);
                case UNCONFIRMED ->
                        throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, CLERK_UNCONFIRMED_MESSAGE);
            }
        }

        if (ban) {
            banEmail(target.getEmail());
        }
        // False means the row was already gone (Clerk's user.deleted webhook can win this
        // race). The account is deleted either way, which is what was asked for.
        boolean removedHere = boundSubject != null ? cleanup.retire(boundSubject) : cleanup.retireUnbound(studentId);
        if (removeContent) {
            content.removeAllBy(boundSubject, target.getEmail());
        }
        log.info("Administrator {} deleted student account {} ({} sign-in(s) removed{}{}{}).", admin, studentId,
                signIns.size(), removedHere ? "" : "; profile was already gone",
                ban ? "; address banned" : "", removeContent ? "; content removed" : "");
    }

    /** Reports students filed about each other from Messages, newest first. */
    public AdminPageResponse<AdminUserReportResponse> userReports(Jwt clerkSession, int page, int size) {
        access.require(clerkSession);
        Page<UserReport> found = userReports.findAll(
                pageRequest(page, size, Sort.by(Sort.Direction.DESC, "createdAt", "id")));
        return AdminPageResponse.of(found, found.getContent().stream()
                .map(report -> new AdminUserReportResponse(report.getId(), report.getReportedEmail(),
                        report.getReporterEmail(), report.getReason(), report.getCreatedAt()))
                .toList());
    }

    /** Dismisses one report once it has been dealt with. Nobody is told. */
    public void dismissUserReport(Jwt clerkSession, long reportId) {
        access.require(clerkSession);
        UserReport report = userReports.findById(reportId)
                .orElseThrow(() -> new SearchNotFoundException("That report no longer exists."));
        userReports.delete(report);
        log.info("Administrator {} dismissed user report {}.", CurrentUser.subjectOf(clerkSession), reportId);
    }

    /**
     * An administrator banning or deleting themselves, or another administrator, is not a
     * student account being managed. Removing an administrator is a configuration change.
     */
    private void refuseAdministratorTarget(StudentAccount target, List<String> signIns, String admin) {
        if (signIns.contains(admin) || access.holdsAdminRole(target.getEmail())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, ADMIN_TARGET_MESSAGE);
        }
    }

    /** The Clerk accounts behind a profile: the one bound to it, or any the webhook saw on its address. */
    private List<String> signInsOf(StudentAccount target) {
        return target.getClerkUserId() != null
                ? List.of(target.getClerkUserId())
                : identities.subjectsForEmail(target.getEmail());
    }

    private void banEmail(String email) {
        Optional<UserDto> row = accountRow(email);
        if (row.isEmpty()) {
            users.insertNewUser(UserRequest.builder().role(AccountBans.BANNED_ROLE).email(email).build());
        } else if (!AccountBans.BANNED_ROLE.equals(row.get().getRole())) {
            users.updateRole(row.get().getId(), AccountBans.BANNED_ROLE);
        }
    }

    /** The account row for an address as stored, or failing that as Clerk would write it. */
    private Optional<UserDto> accountRow(String email) {
        if (email == null || email.isBlank()) {
            return Optional.empty();
        }
        return users.findByEmail(email.trim()).or(() -> users.findByEmail(AdminAccess.normalize(email)));
    }

    private String roleOf(String email) {
        return accountRow(email).map(UserDto::getRole).orElse(null);
    }

    private StudentAccount find(long studentId) {
        return accounts.findById(studentId).orElseThrow(() -> new SearchNotFoundException(NOT_FOUND_MESSAGE));
    }

    private Map<Long, String> schoolNames() {
        return schools.findAll().stream().collect(Collectors.toMap(School::getId, School::getName));
    }

    /** Never hands out the Clerk user id: the SPA needs to know one exists, not what it is. */
    private static AdminStudentResponse toResponse(StudentAccount account, Map<Long, String> schoolNames,
                                                   String role) {
        return new AdminStudentResponse(account.getId(), account.getFirstName(), account.getLastName(),
                account.getEmail(), account.getUniversityId(), schoolNames.get(account.getUniversityId()),
                account.getGrade(), account.getMajor(), account.getResidentCity(), account.getResidentState(),
                account.getSocialMediaLink(), account.getGraduationYear(), account.getBio(),
                account.getClerkUserId() != null, AdminAccess.ADMIN_ROLE.equals(role),
                AccountBans.BANNED_ROLE.equals(role));
    }

    /** A bounded page request: the page is never negative and the size never above {@link #MAX_PAGE_SIZE}. */
    static PageRequest pageRequest(int page, int size, Sort sort) {
        return PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE), sort);
    }

    /**
     * The search text as a LIKE pattern. {@code %} and {@code _} typed by the administrator
     * are escaped so they match themselves, which also stops "%" from being a way to ask for
     * a different result than the text suggests. The value is bound, never concatenated.
     */
    static String patternFor(String query) {
        if (query == null || query.isBlank()) {
            return "%";
        }
        String text = query.trim().toLowerCase(Locale.ROOT);
        if (text.length() > MAX_QUERY_LENGTH) {
            text = text.substring(0, MAX_QUERY_LENGTH);
        }
        return "%" + text.replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}

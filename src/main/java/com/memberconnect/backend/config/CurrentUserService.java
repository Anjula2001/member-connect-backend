package com.memberconnect.backend.config;

import java.util.List;
import java.util.Set;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import com.memberconnect.backend.enums.Permission;
import com.memberconnect.backend.enums.Role;
import com.memberconnect.backend.model.User;

/**
 * Reads the authenticated user out of the security context.
 *
 * Needed wherever authorization cannot be expressed as a single @PreAuthorize on a
 * method — the Grade 5 status-change endpoint is the main case, since the right it
 * requires depends on which status is being requested, not on which method was called.
 *
 * Call {@link #require} from a controller *before* its try/catch. Every Grade 5
 * controller wraps its service call in catch(RuntimeException), and
 * AccessDeniedException extends RuntimeException — so a denial raised inside the try
 * would be swallowed and reported as 400 Bad Request instead of reaching the
 * AccessDeniedException handler in GlobalExceptionHandler and returning 403.
 */
@Component
public class CurrentUserService {

    /** Roles whose remit is national rather than a single District Office. */
    private static final Set<Role> ALL_LOCATION_ROLES = Set.of(
            Role.SUPER_ADMIN,
            Role.HEAD_OFFICE,
            Role.BOARD_SECRETARY,
            Role.ACCOUNTS,
            Role.SCHOLARSHIP_OFFICER);

    /** The authenticated user, or null when the request is unauthenticated. */
    public User current() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof User user)) {
            return null;
        }
        return user;
    }

    public Role currentRole() {
        User user = current();
        return user == null ? null : user.getRole();
    }

    public boolean has(Permission permission) {
        return RolePermissions.has(currentRole(), permission);
    }

    /** Throws AccessDeniedException (-> 403) when the caller lacks the right. */
    public void require(Permission permission) {
        if (!has(permission)) {
            throw new AccessDeniedException("Missing permission: " + permission.name());
        }
    }

    /**
     * True when the caller may see every District Office's records. District Office
     * users see only their own assigned district; everyone else is national.
     */
    public boolean canSeeAllLocations() {
        Role role = currentRole();
        return role != null && ALL_LOCATION_ROLES.contains(role);
    }

    /** True when the caller may only see their own District Office's records. */
    public boolean isLocationRestricted() {
        return !canSeeAllLocations();
    }

    /**
     * The single District Office a location-restricted user is confined to.
     *
     * Returns null when there is no district on their account. Callers must treat that
     * as "show nothing", not "show everything" — a District Office user with no
     * assigned district is a misconfigured account, and defaulting them to national
     * visibility would reopen exactly the leak this scoping exists to close.
     */
    public String restrictedToLocation() {
        if (canSeeAllLocations()) {
            return null;
        }
        User user = current();
        if (user == null) {
            return null;
        }
        String district = user.getAssignedDistrict();
        return (district == null || district.isBlank()) ? null : district;
    }

    /**
     * The set of District Offices a list query may return, once the caller's own
     * restrictions have been applied on top of whatever they asked for.
     *
     * @param filtered  false = no location filtering at all, return everything.
     * @param locations when filtered, the allowed locations. An EMPTY list here means
     *                  "return nothing" — it is not the same as no filtering.
     */
    public record LocationScope(boolean filtered, List<String> locations) {

        public boolean showsNothing() {
            return filtered && locations.isEmpty();
        }
    }

    /**
     * Works out which locations the current user may actually see, given what the
     * client asked for.
     *
     * This exists because {@link #restrictedToLocation()} returns null for two
     * opposite reasons — "you are national" and "you are restricted but have no
     * district on file" — and a caller that cannot tell them apart will resolve a
     * misconfigured account to full visibility, which is the exact leak location
     * scoping is meant to close. Both scholarship modules call this rather than
     * interpreting the nulls themselves, so they cannot drift apart.
     *
     * A restricted user is always pinned to their own district: locations they ask
     * for are ignored rather than rejected, so a stale filter in the UI degrades to
     * "your own district" instead of an error.
     */
    public LocationScope resolveLocationScope(List<String> requestedLocations) {
        if (isLocationRestricted()) {
            String pinned = restrictedToLocation();
            return new LocationScope(true, pinned == null ? List.of() : List.of(pinned));
        }

        if (requestedLocations == null || requestedLocations.isEmpty()) {
            return new LocationScope(false, List.of());
        }

        List<String> cleaned = requestedLocations.stream()
                .filter(location -> location != null && !location.isBlank())
                .filter(location -> !"ALL".equalsIgnoreCase(location))
                .toList();

        // "ALL", or a selection that contained nothing usable, means no filtering.
        return cleaned.isEmpty()
                ? new LocationScope(false, List.of())
                : new LocationScope(true, cleaned);
    }

    /**
     * Whether a record with the given location should be visible under a scope.
     *
     * Records saved before the location column existed carry a null location. They
     * stay visible rather than vanishing, so switching scoping on does not hide
     * historical requests that nobody is able to re-tag.
     */
    public boolean matchesScope(LocationScope scope, String recordLocation) {
        if (!scope.filtered()) {
            return true;
        }
        if (recordLocation == null || recordLocation.isBlank()) {
            return true;
        }
        return scope.locations().stream().anyMatch(recordLocation::equalsIgnoreCase);
    }
}

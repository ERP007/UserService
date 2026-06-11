package com.fallguys.userservice.shared.infrastructure.client;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import com.fallguys.userservice.usermanagement.domain.CreateUserIdentityCommand;
import com.fallguys.userservice.usermanagement.domain.UpdateUserCommand;
import com.fallguys.userservice.shared.domain.model.UserIdentity;
import com.fallguys.userservice.shared.domain.UserIdentityManager;
import com.fallguys.userservice.shared.domain.model.UserIdentityState;
import com.fallguys.userservice.shared.domain.model.UserRole;
import com.fallguys.userservice.shared.domain.model.UserTenancy;
import com.fallguys.userservice.shared.domain.exception.UserAlreadyExistsException;
import com.fallguys.userservice.shared.domain.exception.UserErrorCode;
import com.fallguys.userservice.shared.domain.exception.UserIdentityException;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.ProcessingException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.keycloak.admin.client.CreatedResponseUtil;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.UserResource;
import org.keycloak.admin.client.resource.UsersResource;
import org.keycloak.representations.idm.CredentialRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@Slf4j
@RequiredArgsConstructor
public class KeycloakUserIdentityManager implements UserIdentityManager {

    private static final String EMPLOYEE_NUMBER = "employee_no";
    private static final String TENANCY_CODE = "tenancy_code";
    private static final String TENANCY_TYPE = "tenancy_type";
    private static final String USER_ROLE = "user_role";
    private static final String USER_PROFILE_NAME = "name";
    private static final String USER_PROFILE_ROLE = "Role";
    private static final String POSITION = "position";
    private static final String UPDATE_PASSWORD = "UPDATE_PASSWORD";
    private static final int ERROR_SUMMARY_MAX_LENGTH = 200;
    private static final int ERROR_SUMMARY_PREFIX_LENGTH = 40;
    private static final int ERROR_SUMMARY_SUFFIX_LENGTH = 20;
    private static final String MASK = "***";

    private final Keycloak keycloak;
    private final KeycloakAdminProperties properties;

    @Override
    public Optional<UserIdentity> findById(String keycloakId) {
        try {
            UserRepresentation representation = user(keycloakId).toRepresentation();
            return Optional.of(toIdentity(representation));
        } catch (NotFoundException ex) {
            return Optional.empty();
        } catch (ProcessingException | WebApplicationException ex) {
            throw new UserIdentityException(UserErrorCode.USER_IDENTITY_READ_FAILED, ex);
        }
    }

    @Override
    public Optional<Instant> findPasswordChangedAt(String keycloakId) {
        try {
            return user(keycloakId).credentials().stream()
                    .filter(this::isPasswordCredential)
                    .map(CredentialRepresentation::getCreatedDate)
                    .filter(Objects::nonNull)
                    .max(Long::compareTo)
                    .map(Instant::ofEpochMilli);
        } catch (NotFoundException ex) {
            return Optional.empty();
        } catch (ProcessingException | WebApplicationException ex) {
            throw new UserIdentityException(UserErrorCode.USER_IDENTITY_READ_FAILED, ex);
        }
    }

    @Override
    public UserIdentity create(CreateUserIdentityCommand command) {
        UserRepresentation representation = toRepresentation(command);

        try (Response response = users().create(representation)) {
            int status = response.getStatus();
            if (status == Response.Status.CREATED.getStatusCode()) {
                String keycloakId = CreatedResponseUtil.getCreatedId(response);
                logCreatedUserAttributes(keycloakId);
                return fallbackIdentity(keycloakId, command);
            }

            if (status == Response.Status.CONFLICT.getStatusCode()) {
                throw new UserAlreadyExistsException();
            }

            log.warn("Keycloak 사용자 생성 실패. status={}, errorSummary={}", status, safeErrorSummary(response));
            throw new UserIdentityException(UserErrorCode.USER_IDENTITY_CREATE_FAILED);
        } catch (ProcessingException | WebApplicationException ex) {
            throw new UserIdentityException(UserErrorCode.USER_IDENTITY_CREATE_FAILED, ex);
        }
    }

    @Override
    public void update(UpdateUserCommand command, UserTenancy tenancy) {
        try {
            UserResource userResource = user(command.keycloakId());
            UserRepresentation representation = userResource.toRepresentation();
            representation.setEmail(command.email());
            representation.setFirstName(command.displayName());
            representation.setAttributes(updatedAttributes(representation, command, tenancy));
            userResource.update(representation);
        } catch (NotFoundException ex) {
            throw new UserIdentityException(UserErrorCode.USER_IDENTITY_READ_FAILED, ex);
        } catch (ProcessingException | WebApplicationException ex) {
            throw new UserIdentityException(UserErrorCode.USER_IDENTITY_UPDATE_FAILED, ex);
        }
    }

    @Override
    public void delete(String keycloakId) {
        try {
            user(keycloakId).remove();
        } catch (NotFoundException ex) {
            return;
        } catch (ProcessingException | WebApplicationException ex) {
            throw new UserIdentityException(UserErrorCode.USER_IDENTITY_DELETE_FAILED, ex);
        }
    }

    @Override
    public void resetPassword(String keycloakId, String temporaryPassword) {
        try {
            UserResource userResource = user(keycloakId);
            userResource.resetPassword(passwordCredential(temporaryPassword));
            requirePasswordUpdate(userResource);
        } catch (NotFoundException ex) {
            throw new UserIdentityException(UserErrorCode.USER_IDENTITY_READ_FAILED, ex);
        } catch (ProcessingException | WebApplicationException ex) {
            throw new UserIdentityException(UserErrorCode.USER_IDENTITY_PASSWORD_RESET_FAILED, ex);
        }
    }

    @Override
    public UserIdentityState findState(String keycloakId) {
        try {
            UserRepresentation representation = user(keycloakId).toRepresentation();
            return new UserIdentityState(Boolean.TRUE.equals(representation.isEnabled()), passwordUpdateRequired(representation));
        } catch (NotFoundException ex) {
            throw new UserIdentityException(UserErrorCode.USER_IDENTITY_READ_FAILED, ex);
        } catch (ProcessingException | WebApplicationException ex) {
            throw new UserIdentityException(UserErrorCode.USER_IDENTITY_READ_FAILED, ex);
        }
    }

    @Override
    public void updateEnabled(String keycloakId, boolean enabled) {
        try {
            UserResource userResource = user(keycloakId);
            UserRepresentation representation = userResource.toRepresentation();
            representation.setEnabled(enabled);
            userResource.update(representation);
        } catch (NotFoundException ex) {
            throw new UserIdentityException(UserErrorCode.USER_IDENTITY_READ_FAILED, ex);
        } catch (ProcessingException | WebApplicationException ex) {
            throw new UserIdentityException(UserErrorCode.USER_IDENTITY_ENABLED_UPDATE_FAILED, ex);
        }
    }

    private UsersResource users() {
        return keycloak.realm(properties.realm()).users();
    }

    private UserResource user(String keycloakId) {
        return users().get(keycloakId);
    }

    private void logCreatedUserAttributes(String keycloakId) {
        if (!log.isDebugEnabled()) {
            return;
        }

        try {
            UserRepresentation representation = user(keycloakId).toRepresentation();
            log.debug("Keycloak 사용자 attribute 조회. keycloakId={}, attributes={}",
                    keycloakId,
                    maskedAttributes(representation.getAttributes()));
        } catch (ProcessingException | WebApplicationException ex) {
            log.debug("Keycloak 생성 사용자 attribute 조회 실패. keycloakId={}", keycloakId, ex);
        }
    }

    private String safeErrorSummary(Response response) {
        if (!response.hasEntity()) {
            return "";
        }

        try {
            String body = response.readEntity(String.class);
            if (!StringUtils.hasText(body)) {
                return "";
            }

            String normalized = body.replaceAll("\\s+", " ").trim();
            String truncated = normalized.length() > ERROR_SUMMARY_MAX_LENGTH
                    ? normalized.substring(0, ERROR_SUMMARY_MAX_LENGTH)
                    : normalized;
            return maskErrorSummary(truncated);
        } catch (RuntimeException ex) {
            return "[unreadable]";
        }
    }

    private String maskErrorSummary(String value) {
        if (value.length() <= 8) {
            return MASK;
        }

        if (value.length() <= ERROR_SUMMARY_PREFIX_LENGTH + ERROR_SUMMARY_SUFFIX_LENGTH) {
            int edgeLength = Math.min(4, value.length() / 3);
            return value.substring(0, edgeLength) + MASK + value.substring(value.length() - edgeLength);
        }

        return value.substring(0, ERROR_SUMMARY_PREFIX_LENGTH)
                + MASK
                + value.substring(value.length() - ERROR_SUMMARY_SUFFIX_LENGTH);
    }

    private UserRepresentation toRepresentation(CreateUserIdentityCommand command) {
        UserRepresentation representation = new UserRepresentation();
        representation.setUsername(command.employeeNumber());
        representation.setEmail(command.email());
        representation.setFirstName(command.displayName());
        representation.setEnabled(true);
        representation.setAttributes(attributes(command));
        representation.setCredentials(List.of(passwordCredential(command)));
        representation.setRequiredActions(List.of(UPDATE_PASSWORD));
        return representation;
    }

    private CredentialRepresentation passwordCredential(CreateUserIdentityCommand command) {
        return passwordCredential(command.initialPassword());
    }

    private CredentialRepresentation passwordCredential(String temporaryPassword) {
        CredentialRepresentation credential = new CredentialRepresentation();
        credential.setType(CredentialRepresentation.PASSWORD);
        credential.setValue(temporaryPassword);
        credential.setTemporary(true);
        return credential;
    }

    private void requirePasswordUpdate(UserResource userResource) {
        UserRepresentation representation = userResource.toRepresentation();
        List<String> requiredActions = representation.getRequiredActions();
        List<String> updatedRequiredActions = requiredActions == null
                ? new ArrayList<>()
                : new ArrayList<>(requiredActions);

        if (!updatedRequiredActions.contains(UPDATE_PASSWORD)) {
            updatedRequiredActions.add(UPDATE_PASSWORD);
            representation.setRequiredActions(updatedRequiredActions);
            userResource.update(representation);
        }
    }

    private Map<String, List<String>> attributes(CreateUserIdentityCommand command) {
        Map<String, List<String>> attributes = new HashMap<>();
        attributes.put(EMPLOYEE_NUMBER, List.of(command.employeeNumber()));
        attributes.put(USER_PROFILE_NAME, List.of(command.displayName()));
        attributes.put(TENANCY_CODE, List.of(command.tenancyCode()));
        attributes.put(TENANCY_TYPE, List.of(command.tenancy().name()));
        attributes.put(USER_ROLE, List.of(command.role().name()));
        attributes.put(USER_PROFILE_ROLE, List.of(command.role().name()));
        if (StringUtils.hasText(command.position())) {
            attributes.put(POSITION, List.of(command.position()));
        }

        return attributes;
    }

    private Map<String, List<String>> updatedAttributes(
            UserRepresentation representation,
            UpdateUserCommand command,
            UserTenancy tenancy
    ) {
        Map<String, List<String>> attributes = representation.getAttributes() == null
                ? new HashMap<>()
                : new HashMap<>(representation.getAttributes());
        attributes.put(USER_PROFILE_NAME, List.of(command.displayName()));
        attributes.put(TENANCY_CODE, List.of(command.tenancyCode()));
        attributes.put(TENANCY_TYPE, List.of(tenancy.name()));
        attributes.put(USER_ROLE, List.of(command.role().name()));
        attributes.put(USER_PROFILE_ROLE, List.of(command.role().name()));
        if (StringUtils.hasText(command.position())) {
            attributes.put(POSITION, List.of(command.position()));
        } else {
            attributes.remove(POSITION);
        }

        return attributes;
    }

    private UserIdentity fallbackIdentity(String keycloakId, CreateUserIdentityCommand command) {
        return new UserIdentity(
                keycloakId,
                command.employeeNumber(),
                command.email(),
                command.displayName(),
                command.tenancyCode(),
                command.position(),
                command.role(),
                command.tenancy(),
                true
        );
    }

    private UserIdentity toIdentity(UserRepresentation representation) {
        UserRole role = UserRole.fromClaim(firstText(
                        attribute(representation, USER_ROLE),
                        attribute(representation, USER_PROFILE_ROLE)
                ))
                .orElseThrow(() -> new UserIdentityException(UserErrorCode.USER_IDENTITY_READ_FAILED));
        UserTenancy tenancy = UserTenancy.fromClaim(attribute(representation, TENANCY_TYPE))
                .orElseThrow(() -> new UserIdentityException(UserErrorCode.USER_IDENTITY_READ_FAILED));

        return new UserIdentity(
                representation.getId(),
                firstText(attribute(representation, EMPLOYEE_NUMBER), representation.getUsername()),
                representation.getEmail(),
                firstText(attribute(representation, USER_PROFILE_NAME), displayName(representation), representation.getUsername()),
                attribute(representation, TENANCY_CODE),
                attribute(representation, POSITION),
                role,
                tenancy,
                Boolean.TRUE.equals(representation.isEnabled())
        );
    }

    private String displayName(UserRepresentation representation) {
        String firstName = representation.getFirstName();
        String lastName = representation.getLastName();
        if (!StringUtils.hasText(firstName)) {
            return lastName;
        }
        if (!StringUtils.hasText(lastName)) {
            return firstName;
        }

        return firstName + " " + lastName;
    }

    private String attribute(UserRepresentation representation, String name) {
        Map<String, List<String>> attributes = representation.getAttributes();
        if (attributes == null) {
            return null;
        }

        List<String> values = attributes.get(name);
        if (values == null || values.isEmpty()) {
            return null;
        }

        return values.get(0);
    }

    private boolean passwordUpdateRequired(UserRepresentation representation) {
        List<String> requiredActions = representation.getRequiredActions();
        return requiredActions != null && requiredActions.contains(UPDATE_PASSWORD);
    }

    private boolean isPasswordCredential(CredentialRepresentation credential) {
        return credential != null && CredentialRepresentation.PASSWORD.equals(credential.getType());
    }

    private Map<String, List<String>> maskedAttributes(Map<String, List<String>> attributes) {
        if (attributes == null || attributes.isEmpty()) {
            return Map.of();
        }

        Map<String, List<String>> maskedAttributes = new HashMap<>();
        attributes.forEach((name, values) -> maskedAttributes.put(
                name,
                values == null ? List.of() : values.stream()
                        .map(value -> maskAttributeValue(name, value))
                        .toList()
        ));

        return maskedAttributes;
    }

    private String maskAttributeValue(String name, String value) {
        if (!StringUtils.hasText(value)) {
            return value;
        }
        if (isSecretAttribute(name)) {
            return "***";
        }
        if (isPersonalAttribute(name)) {
            return maskMiddle(value);
        }

        return value;
    }

    private boolean isSecretAttribute(String name) {
        String normalizedName = name.toLowerCase();
        return normalizedName.contains("password")
                || normalizedName.contains("token")
                || normalizedName.contains("secret");
    }

    private boolean isPersonalAttribute(String name) {
        return EMPLOYEE_NUMBER.equals(name)
                || USER_PROFILE_NAME.equals(name)
                || POSITION.equals(name)
                || "email".equalsIgnoreCase(name)
                || "username".equalsIgnoreCase(name);
    }

    private String maskMiddle(String value) {
        if (value.length() <= 2) {
            return "*".repeat(value.length());
        }

        return value.charAt(0) + "*".repeat(value.length() - 2) + value.charAt(value.length() - 1);
    }

    private String firstText(String first, String second, String third) {
        return firstText(firstText(first, second), third);
    }

    private String firstText(String first, String second) {
        if (StringUtils.hasText(first)) {
            return first;
        }

        return second;
    }
}

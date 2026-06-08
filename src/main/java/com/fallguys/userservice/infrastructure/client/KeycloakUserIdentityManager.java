package com.fallguys.userservice.infrastructure.client;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.fallguys.userservice.domain.CreateUserCommand;
import com.fallguys.userservice.domain.UserIdentity;
import com.fallguys.userservice.domain.UserIdentityManager;
import com.fallguys.userservice.domain.UserRole;
import com.fallguys.userservice.domain.UserTenancy;
import com.fallguys.userservice.domain.exception.UserAlreadyExistsException;
import com.fallguys.userservice.domain.exception.UserErrorCode;
import com.fallguys.userservice.domain.exception.UserIdentityException;
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
    public UserIdentity create(CreateUserCommand command) {
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

            log.warn("Keycloak 사용자 생성 실패. status={}, responseBody={}", status, responseBody(response));
            throw new UserIdentityException(UserErrorCode.USER_IDENTITY_CREATE_FAILED);
        } catch (ProcessingException | WebApplicationException ex) {
            throw new UserIdentityException(UserErrorCode.USER_IDENTITY_CREATE_FAILED, ex);
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

    private String responseBody(Response response) {
        if (!response.hasEntity()) {
            return "";
        }

        return response.readEntity(String.class);
    }

    private UserRepresentation toRepresentation(CreateUserCommand command) {
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

    private CredentialRepresentation passwordCredential(CreateUserCommand command) {
        CredentialRepresentation credential = new CredentialRepresentation();
        credential.setType(CredentialRepresentation.PASSWORD);
        credential.setValue(command.initialPassword());
        credential.setTemporary(true);
        return credential;
    }

    private Map<String, List<String>> attributes(CreateUserCommand command) {
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

    private UserIdentity fallbackIdentity(String keycloakId, CreateUserCommand command) {
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

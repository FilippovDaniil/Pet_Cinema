package com.cinema.auth.security;

import com.cinema.auth.entity.Role;
import com.cinema.auth.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("JwtUtils Unit Tests")
class JwtUtilsTest {

    private JwtUtils jwtUtils;

    private static final String SECRET = "testSecretKeyForTestingPurposesOnly1234567890123456";
    private static final long ACCESS_EXPIRY = 900_000L;   // 15 min
    private static final long REFRESH_EXPIRY = 604_800_000L; // 7 days

    @BeforeEach
    void setUp() {
        jwtUtils = new JwtUtils();
        ReflectionTestUtils.setField(jwtUtils, "jwtSecret", SECRET);
        ReflectionTestUtils.setField(jwtUtils, "accessExpiration", ACCESS_EXPIRY);
        ReflectionTestUtils.setField(jwtUtils, "refreshExpiration", REFRESH_EXPIRY);
    }

    private User buildUser(Long id, Role role) {
        return User.builder()
                .id(id)
                .username("user")
                .email("u@test.com")
                .password("pw")
                .role(role)
                .build();
    }

    // -------------------------------------------------------------------------
    // generateAccessToken
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("generateAccessToken: token contains userId as subject and role claim")
    void generateAccessToken_shouldContainUserIdAndRole() {
        User user = buildUser(42L, Role.ROLE_CLIENT);

        String token = jwtUtils.generateAccessToken(user);

        assertThat(token).isNotBlank();
        assertThat(jwtUtils.getUserIdFromToken(token)).isEqualTo("42");
        List<String> roles = jwtUtils.getRolesFromToken(token);
        assertThat(roles).containsExactly("ROLE_CLIENT");
    }

    @Test
    @DisplayName("generateAccessToken: token is valid immediately after creation")
    void generateAccessToken_tokenIsValidRightAfterCreation() {
        User user = buildUser(1L, Role.ROLE_CLIENT);

        String token = jwtUtils.generateAccessToken(user);

        assertThat(jwtUtils.validateToken(token)).isTrue();
    }

    // -------------------------------------------------------------------------
    // generateRefreshToken
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("generateRefreshToken: token contains correct userId as subject")
    void generateRefreshToken_shouldContainUserId() {
        User user = buildUser(7L, Role.ROLE_ADMIN);

        String token = jwtUtils.generateRefreshToken(user);

        assertThat(jwtUtils.getUserIdFromToken(token)).isEqualTo("7");
    }

    @Test
    @DisplayName("generateRefreshToken: token is valid immediately after creation")
    void generateRefreshToken_tokenIsValid() {
        User user = buildUser(3L, Role.ROLE_SELLER);

        String token = jwtUtils.generateRefreshToken(user);

        assertThat(jwtUtils.validateToken(token)).isTrue();
    }

    // -------------------------------------------------------------------------
    // validateToken
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("validateToken: valid token returns true")
    void validateToken_validToken_returnsTrue() {
        User user = buildUser(1L, Role.ROLE_CLIENT);
        String token = jwtUtils.generateAccessToken(user);

        assertThat(jwtUtils.validateToken(token)).isTrue();
    }

    @Test
    @DisplayName("validateToken: expired token returns false")
    void validateToken_expiredToken_returnsFalse() throws InterruptedException {
        JwtUtils shortLivedUtils = new JwtUtils();
        ReflectionTestUtils.setField(shortLivedUtils, "jwtSecret", SECRET);
        ReflectionTestUtils.setField(shortLivedUtils, "accessExpiration", 1L);  // 1 ms
        ReflectionTestUtils.setField(shortLivedUtils, "refreshExpiration", 1L);

        User user = buildUser(1L, Role.ROLE_CLIENT);
        String token = shortLivedUtils.generateAccessToken(user);

        Thread.sleep(10); // ensure expiry

        assertThat(jwtUtils.validateToken(token)).isFalse();
    }

    @Test
    @DisplayName("validateToken: malformed token returns false")
    void validateToken_malformedToken_returnsFalse() {
        assertThat(jwtUtils.validateToken("not.a.valid.jwt")).isFalse();
    }

    @Test
    @DisplayName("validateToken: empty string returns false")
    void validateToken_emptyToken_returnsFalse() {
        assertThat(jwtUtils.validateToken("")).isFalse();
    }

    @Test
    @DisplayName("validateToken: tampered signature returns false")
    void validateToken_tamperedSignature_returnsFalse() {
        User user = buildUser(1L, Role.ROLE_CLIENT);
        String token = jwtUtils.generateAccessToken(user);
        // Replace the signature segment with garbage
        String tampered = token.substring(0, token.lastIndexOf('.')) + ".invalidsignature";

        assertThat(jwtUtils.validateToken(tampered)).isFalse();
    }

    @Test
    @DisplayName("validateToken: token signed with different secret returns false")
    void validateToken_differentSecretToken_returnsFalse() {
        JwtUtils otherUtils = new JwtUtils();
        ReflectionTestUtils.setField(otherUtils, "jwtSecret", "anotherSecretKeyForTestingOnlyXYZ1234567890");
        ReflectionTestUtils.setField(otherUtils, "accessExpiration", ACCESS_EXPIRY);
        ReflectionTestUtils.setField(otherUtils, "refreshExpiration", REFRESH_EXPIRY);

        User user = buildUser(99L, Role.ROLE_ADMIN);
        String foreignToken = otherUtils.generateAccessToken(user);

        assertThat(jwtUtils.validateToken(foreignToken)).isFalse();
    }

    // -------------------------------------------------------------------------
    // getRolesFromToken
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("getRolesFromToken: ROLE_ADMIN is extracted correctly")
    void getRolesFromToken_adminRole() {
        User user = buildUser(5L, Role.ROLE_ADMIN);
        String token = jwtUtils.generateAccessToken(user);

        assertThat(jwtUtils.getRolesFromToken(token)).containsExactly("ROLE_ADMIN");
    }

    @Test
    @DisplayName("getRolesFromToken: ROLE_SELLER is extracted correctly")
    void getRolesFromToken_sellerRole() {
        User user = buildUser(3L, Role.ROLE_SELLER);
        String token = jwtUtils.generateAccessToken(user);

        assertThat(jwtUtils.getRolesFromToken(token)).containsExactly("ROLE_SELLER");
    }

    @Test
    @DisplayName("getRolesFromToken: ROLE_CLIENT is extracted correctly")
    void getRolesFromToken_clientRole() {
        User user = buildUser(8L, Role.ROLE_CLIENT);
        String token = jwtUtils.generateAccessToken(user);

        assertThat(jwtUtils.getRolesFromToken(token)).containsExactly("ROLE_CLIENT");
    }

    // -------------------------------------------------------------------------
    // getUserIdFromToken
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("getUserIdFromToken: extracts correct user id for various users")
    void getUserIdFromToken_variousIds() {
        long[] ids = {1L, 100L, 999999L};
        for (long id : ids) {
            User user = buildUser(id, Role.ROLE_CLIENT);
            String token = jwtUtils.generateAccessToken(user);
            assertThat(jwtUtils.getUserIdFromToken(token)).isEqualTo(String.valueOf(id));
        }
    }

    // -------------------------------------------------------------------------
    // Access token vs Refresh token are independent
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Access and refresh tokens for the same user have the same subject")
    void accessAndRefreshTokens_sameSubject() {
        User user = buildUser(11L, Role.ROLE_CLIENT);

        String access = jwtUtils.generateAccessToken(user);
        String refresh = jwtUtils.generateRefreshToken(user);

        assertThat(jwtUtils.getUserIdFromToken(access))
                .isEqualTo(jwtUtils.getUserIdFromToken(refresh))
                .isEqualTo("11");
    }

    @Test
    @DisplayName("Access and refresh tokens for the same user are not identical strings")
    void accessAndRefreshTokens_areDifferentStrings() {
        User user = buildUser(11L, Role.ROLE_CLIENT);

        String access = jwtUtils.generateAccessToken(user);
        String refresh = jwtUtils.generateRefreshToken(user);

        assertThat(access).isNotEqualTo(refresh);
    }
}

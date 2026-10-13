package com.evision.ragservice.adapters.web;

import com.evision.ragservice.application.ClientService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.server.PathContainer;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ServletRequestPathUtils;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;
import tools.jackson.databind.ObjectMapper;

@Component
public class ApiKeyFilter extends OncePerRequestFilter {

    public static final String CLIENT_ID_ATTRIBUTE = "rag.clientId";

    private static final int MIN_ADMIN_KEY_LENGTH = 32;
    private static final PathPattern ADMIN = PathPatternParser.defaultInstance.parse("/v1/admin/**");
    private static final List<PathPattern> PUBLIC = List.of(
            "/actuator/**", "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").stream()
            .map(PathPatternParser.defaultInstance::parse).toList();
    private static final String BEARER = "bearer ";

    private final ClientService clients;
    private final ObjectMapper objectMapper;
    private final byte[] adminKeyBytes;

    public ApiKeyFilter(ClientService clients, ObjectMapper objectMapper,
                        @Value("${rag.admin-key}") String adminKey) {
        if (adminKey == null || adminKey.length() < MIN_ADMIN_KEY_LENGTH) {
            throw new IllegalStateException("rag.admin-key (RAG_ADMIN_KEY) must be at least 32 characters");
        }
        this.clients = clients;
        this.objectMapper = objectMapper;
        this.adminKeyBytes = adminKey.getBytes(StandardCharsets.UTF_8);
    }

    // Fail closed, and match the decoded path segments MVC routes on, never the raw URI
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        PathContainer path = lookupPath(request);
        return PUBLIC.stream().anyMatch(pattern -> pattern.matches(path));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String token = bearerToken(request);
        if (token.isEmpty()) {
            reject(response);
            return;
        }
        if (ADMIN.matches(lookupPath(request))) {
            if (!MessageDigest.isEqual(token.getBytes(StandardCharsets.UTF_8), adminKeyBytes)) {
                reject(response);
                return;
            }
        } else {
            Optional<UUID> clientId = clients.authenticate(token);
            if (clientId.isEmpty()) {
                reject(response);
                return;
            }
            request.setAttribute(CLIENT_ID_ATTRIBUTE, clientId.get());
        }
        chain.doFilter(request, response);
    }

    private static PathContainer lookupPath(HttpServletRequest request) {
        return ServletRequestPathUtils.parseAndCache(request).pathWithinApplication();
    }

    private static String bearerToken(HttpServletRequest request) {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.regionMatches(true, 0, BEARER, 0, BEARER.length())) {
            return "";
        }
        return header.substring(BEARER.length()).trim();
    }

    private void reject(HttpServletResponse response) throws IOException {
        ProblemDetail pd = ProblemDetail.forStatus(HttpStatus.UNAUTHORIZED);
        pd.setTitle("Invalid API key");
        pd.setDetail("Provide a valid API key in the Authorization header as 'Bearer <key>'.");
        pd.setProperty("reason", "INVALID_API_KEY");
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(), pd);
    }
}

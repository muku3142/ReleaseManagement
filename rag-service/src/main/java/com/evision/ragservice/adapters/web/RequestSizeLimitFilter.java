package com.evision.ragservice.adapters.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

@Component
@Order(0)
public class RequestSizeLimitFilter extends OncePerRequestFilter {

    private final ObjectMapper objectMapper;
    private final long maxRequestBytes;

    public RequestSizeLimitFilter(ObjectMapper objectMapper,
                                  @Value("${rag.limits.max-request-bytes}") long maxRequestBytes) {
        this.objectMapper = objectMapper;
        this.maxRequestBytes = maxRequestBytes;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/v1/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (request.getContentLengthLong() > maxRequestBytes) {
            ProblemDetail pd = ProblemDetail.forStatus(HttpStatus.PAYLOAD_TOO_LARGE);
            pd.setTitle("Payload too large");
            pd.setDetail("Request body exceeds the limit of " + maxRequestBytes + " bytes");
            pd.setProperty("reason", "PAYLOAD_TOO_LARGE");
            response.setStatus(HttpStatus.PAYLOAD_TOO_LARGE.value());
            response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
            objectMapper.writeValue(response.getWriter(), pd);
            return;
        }
        chain.doFilter(request, response);
    }
}

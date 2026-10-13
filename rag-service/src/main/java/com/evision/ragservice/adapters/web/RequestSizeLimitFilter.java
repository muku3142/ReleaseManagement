package com.evision.ragservice.adapters.web;

import com.evision.ragservice.domain.PayloadTooLargeException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
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
        // Chunked bodies declare no length, so count bytes as they are read
        chain.doFilter(request.getContentLengthLong() < 0 ? new BoundedRequest(request, maxRequestBytes) : request,
                response);
    }

    private static final class BoundedRequest extends HttpServletRequestWrapper {

        private final long limit;
        private ServletInputStream bounded;

        BoundedRequest(HttpServletRequest request, long limit) {
            super(request);
            this.limit = limit;
        }

        @Override
        public ServletInputStream getInputStream() throws IOException {
            if (bounded == null) {
                bounded = new BoundedInputStream(super.getInputStream(), limit);
            }
            return bounded;
        }
    }

    private static final class BoundedInputStream extends ServletInputStream {

        private final ServletInputStream delegate;
        private final long limit;
        private long read;

        BoundedInputStream(ServletInputStream delegate, long limit) {
            this.delegate = delegate;
            this.limit = limit;
        }

        @Override
        public int read() throws IOException {
            int b = delegate.read();
            if (b >= 0) {
                count(1);
            }
            return b;
        }

        @Override
        public int read(byte[] buffer, int offset, int length) throws IOException {
            int n = delegate.read(buffer, offset, length);
            if (n > 0) {
                count(n);
            }
            return n;
        }

        private void count(int n) {
            read += n;
            if (read > limit) {
                throw new PayloadTooLargeException(limit);
            }
        }

        @Override
        public boolean isFinished() {
            return delegate.isFinished();
        }

        @Override
        public boolean isReady() {
            return delegate.isReady();
        }

        @Override
        public void setReadListener(ReadListener listener) {
            delegate.setReadListener(listener);
        }
    }
}

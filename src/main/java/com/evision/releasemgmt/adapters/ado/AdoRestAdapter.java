package com.evision.releasemgmt.adapters.ado;

import com.evision.releasemgmt.domain.ado.AdoAccessException;
import com.evision.releasemgmt.domain.ado.AdoFailureReason;
import com.evision.releasemgmt.ports.AdoRepositoryPort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

@Component
public class AdoRestAdapter implements AdoRepositoryPort {

    private static final int NON_AUTHORITATIVE = 203;

    private final RestClient adoRestClient;
    private final String baseUrl;

    // baseUrl is configuration (tests point it at WireMock), never request input; production is https://dev.azure.com.
    public AdoRestAdapter(RestClient adoRestClient, @Value("${app.ado.base-url}") String baseUrl) {
        this.adoRestClient = adoRestClient;
        this.baseUrl = baseUrl;
    }

    @Override
    public void validate(String organization, String token) {
        try {
            adoRestClient.get()
                    .uri(baseUrl + "/{organization}/_apis/git/repositories?api-version=7.1", organization)
                    .header(HttpHeaders.AUTHORIZATION, basic(token))
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .onStatus(s -> s.is3xxRedirection() || s.value() == NON_AUTHORITATIVE
                                    || s.value() == 401 || s.value() == 403,
                            (req, res) -> {
                                throw new AdoAccessException(AdoFailureReason.UNAUTHORIZED);
                            })
                    .toBodilessEntity();
        } catch (AdoAccessException e) {
            throw e;
        } catch (HttpClientErrorException.NotFound e) {
            throw new AdoAccessException(AdoFailureReason.NOT_FOUND);
        } catch (HttpClientErrorException.TooManyRequests e) {
            throw new AdoAccessException(AdoFailureReason.RATE_LIMITED);
        } catch (HttpServerErrorException e) {
            throw new AdoAccessException(AdoFailureReason.ERROR);
        } catch (ResourceAccessException e) {
            throw new AdoAccessException(AdoFailureReason.UNREACHABLE);
        } catch (RestClientException e) {
            throw new AdoAccessException(AdoFailureReason.ERROR);
        }
    }

    private static String basic(String token) {
        return "Basic " + Base64.getEncoder().encodeToString((":" + token).getBytes(StandardCharsets.UTF_8));
    }
}

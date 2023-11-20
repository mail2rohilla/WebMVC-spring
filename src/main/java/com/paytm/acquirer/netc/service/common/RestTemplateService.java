package com.paytm.acquirer.netc.service.common;

import com.paytm.acquirer.netc.exception.RestServiceErrorHandler;
import com.paytm.transport.LoggingClientHttpRequestInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import javax.annotation.PostConstruct;
import javax.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
public class RestTemplateService {

    @Autowired
    private LoggingClientHttpRequestInterceptor loggingClientHttpRequestInterceptor;

    private final RestTemplate restTemplate;

    public RestTemplateService(@Qualifier("restTemplateWithSsl") RestTemplate restTemplate) {
        restTemplate.setErrorHandler(new RestServiceErrorHandler());
        this.restTemplate = restTemplate;
    }

    @PostConstruct
    private void setup() {
        List<ClientHttpRequestInterceptor> interceptors = this.restTemplate.getInterceptors();
        if (CollectionUtils.isEmpty(interceptors)) {
            interceptors = new ArrayList<>();
        }
        interceptors.add(loggingClientHttpRequestInterceptor);
        this.restTemplate.setInterceptors(interceptors);
    }

    /**
     * GET request with query params
     *
     * @param url         Request URL
     * @param returnType  Type of response body
     * @param queryParams Http query parameters
     * @param <T>         Return type
     * @return Response body casted to type <T> if request succeed
     */
    public <T> T executeGetRequest(@NotNull final String url, @NotNull final Class<T> returnType,
                                   @NotNull final MultiValueMap<String, String> queryParams) {
        validateQueryParams(queryParams);
        return executeGetRequest(url, returnType, Collections.emptyMap(), queryParams);
    }

    /**
     * POST request with custom headers and query params
     *
     * @param url         Request URL
     * @param returnType  Type of response body
     * @param headers     Http headers
     * @param queryParams Http query parameters
     * @param <T>         Return type
     * @return Response body casted to type <T> if request succeed
     */
    public <T> T executePostRequest(@NotNull final String url, @NotNull final Class<T> returnType,
                                    @NotNull final Map<String, String> headers,
                                    @NotNull final MultiValueMap<String, String> queryParams) {
        Assert.notEmpty(headers, "Headers Can't be null or empty");
        validateQueryParams(queryParams);
        return executePostRequest(url, returnType, null, headers, queryParams);
    }

    /**
     * POST request with body and custom query params
     *
     * @param url         Request URL
     * @param returnType  Type of response body
     * @param requestBody Request body
     * @param queryParams Http query params
     * @param <T>         Return type
     * @param <R>         Request body type
     * @return Response body casted to type <T> if request succeed
     * @throws HttpStatusCodeException if request failed (4XX or 5XX)
     */
    public <T, R> T executePostRequest(@NotNull final String url, @NotNull final Class<T> returnType,
                                       @NotNull final R requestBody,
                                       @NotNull final MultiValueMap<String, String> queryParams) {
        Assert.notNull(requestBody, "RequestBody can't be null");
        validateQueryParams(queryParams);
        return executePostRequest(url, returnType, requestBody, Collections.emptyMap(), queryParams);
    }

    private void validateQueryParams(@NotNull MultiValueMap<String, String> queryParams) {
        Assert.notEmpty(queryParams, "Query params can't be null or empty");
    }

    /**
     * POST request with just body
     *
     * @param url         Request URL
     * @param returnType  Type of response body
     * @param requestBody Request body
     * @param <T>         Return type
     * @param <R>         Request body type
     * @return Response body casted to type <T> if request succeed
     * @throws HttpStatusCodeException if request failed (4XX or 5XX)
     */
    public <T, R> T executePostRequest(@NotNull final String url, @NotNull final Class<T> returnType,
                                       @NotNull final R requestBody) {
        Assert.notNull(requestBody, "RequestBody can't be null");
        return executePostRequest(url, returnType, requestBody, Collections.emptyMap(),
                CollectionUtils.toMultiValueMap(Collections.emptyMap()));
    }

    /**
     * Generic POST request with headers and queryParams
     *
     * @param url         Request URL
     * @param returnType  Type of response body
     * @param requestBody Request body
     * @param headers     Http headers
     * @param queryParams Http query parameters
     * @param <T>         Return type
     * @param <R>         Request body type
     * @return Response body casted to type <T> if request succeed
     * @throws HttpStatusCodeException if request failed (4XX or 5XX)
     */
    public <T, R> T executePostRequest(@NotNull final String url, @NotNull final Class<T> returnType,
                                       final R requestBody, final Map<String, String> headers,
                                       MultiValueMap<String, ?> queryParams) {

        return executePostRequestReturnResponseEntity(url, returnType, requestBody, headers, queryParams).getBody();
    }

    public <T, R> ResponseEntity<T> executePostRequestReturnResponseEntity(
            @NotNull final String url, @NotNull final Class<T> returnType,
            final R requestBody, final Map<String, String> headers,
            MultiValueMap<String, ?> queryParams) {
        return executeRequestGetResponseEntity(url, HttpMethod.POST, returnType, requestBody, headers, queryParams);
    }

    /**
     * Generic GET request with headers and queryParams
     *
     * @param url         Request URL
     * @param returnType  Type of response body
     * @param headers     Http headers
     * @param queryParams Http query parameters
     * @param <T>         return type
     * @return Response body casted to type <T> if request succeed
     * @throws HttpStatusCodeException if request failed (4XX or 5XX)
     */
    public <T> T executeGetRequest(@NotNull final String url, @NotNull final Class<T> returnType,
                                   final Map<String, String> headers,
                                   MultiValueMap<String, ?> queryParams) {

        return executeRequestGetResponseEntity(url, HttpMethod.GET, returnType, null, headers, queryParams)
          .getBody();
    }

    private <T, R> ResponseEntity<T> executeRequestGetResponseEntity(String url, HttpMethod httpMethod,
      Class<T> returnType, R requestBody, Map<String, String> headers, MultiValueMap<String, ?> queryParams) {

        Assert.hasLength(url, "Url can't be null or blank");
        Assert.notNull(returnType, "Class type cant be null");

        HttpHeaders httpHeaders = new HttpHeaders();
        if (Objects.nonNull(headers)) {
            headers.forEach(httpHeaders::add);
        }
        if (queryParams == null) {
            queryParams = CollectionUtils.toMultiValueMap(Collections.emptyMap());
        }

        HttpEntity<Object> httpEntity = new HttpEntity<>(requestBody, httpHeaders);
        return restTemplate.exchange(url, httpMethod, httpEntity, returnType, queryParams);
    }

}

package com.paytm.acquirer.netc.config;

import com.paytm.transport.logger.Logger;
import com.paytm.transport.logger.LoggerFactory;
import org.apache.http.HttpResponse;
import org.apache.http.NoHttpResponseException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.HttpRequestRetryHandler;
import org.apache.http.conn.ConnectionKeepAliveStrategy;
import org.apache.http.impl.client.DefaultConnectionKeepAliveStrategy;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.protocol.HttpContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.Resource;
import org.springframework.http.client.BufferingClientHttpRequestFactory;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import java.io.IOException;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.concurrent.TimeUnit;

@Configuration
public class RestApiConfig {

    private static final Logger log = LoggerFactory.getLogger(RestApiConfig.class);
    
    @Value("${netc.ssl.certificate}")
    private Resource certificate;

    @Value("${netc.request.timeout-ms}")
    private Integer requestTimeout;

    @Value("${netc.request.connection-ms}")
    private Integer connectionTimeout;

    // Only enable SSL on efkon/netc servers
    @Bean("restTemplateWithSsl")
    @Profile({"uat", "prod", "uaws", "paws"})
    public RestTemplate restTemplate() throws CertificateException, KeyStoreException, NoSuchAlgorithmException,
      IOException, KeyManagementException {
        HttpClient httpClient = HttpClientBuilder
                .create()
                .setSSLContext(getSslContextFromCertificate())
                .setSSLHostnameVerifier(getHostnameVerifier())
                .setMaxConnTotal(500)
                .setMaxConnPerRoute(50)
                .disableCookieManagement()
                .setRetryHandler(getRetryHandler())
                .setKeepAliveStrategy(getKeepAliveConfig())
                .setConnectionTimeToLive(10, TimeUnit.SECONDS)
                .build();
        
        return new RestTemplate(new BufferingClientHttpRequestFactory(getRequestFactory(httpClient)));
    }

    private ConnectionKeepAliveStrategy getKeepAliveConfig() {
        return new DefaultConnectionKeepAliveStrategy() {
            @Override
            public long getKeepAliveDuration(HttpResponse response, HttpContext context) {
                long defaultValue = super.getKeepAliveDuration(response, context);

                // If server has return a keepAlive value in response use that value
                // or else use our custom value.
                return (defaultValue == -1L) ? 10 * 1000L : defaultValue;
            }
        };
    }

    /**
     * Retry 3 times if got {@link NoHttpResponseException}
     * @return true if request should be retried
     */
    private HttpRequestRetryHandler getRetryHandler() {
        return (iox, executionCount, httpContext) -> {
            if (iox instanceof NoHttpResponseException) {
                return executionCount <= 3;
            }
            return false;
        };
    }

    @Bean("restTemplateWithSsl")
    @Profile({"dev", "staging","staging2"})
    public RestTemplate basic() {
        HttpClient httpClient = HttpClientBuilder.create().build();
        return new RestTemplate(new BufferingClientHttpRequestFactory(getRequestFactory(httpClient)));
    }

    private HttpComponentsClientHttpRequestFactory getRequestFactory(HttpClient httpClient) {
        HttpComponentsClientHttpRequestFactory requestFactory = new HttpComponentsClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(requestTimeout);
        requestFactory.setConnectTimeout(requestTimeout);
        requestFactory.setConnectionRequestTimeout(connectionTimeout);
        return requestFactory;
    }

    private SSLContext getSslContextFromCertificate() throws CertificateException, KeyStoreException,
      NoSuchAlgorithmException, IOException, KeyManagementException {
        CertificateFactory cf = CertificateFactory.getInstance("X.509");
        X509Certificate caCert = (X509Certificate) cf.generateCertificate(certificate.getInputStream());

        log.debug("Certificate file name : {}", certificate.getFilename());

        TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        KeyStore ks = KeyStore.getInstance(KeyStore.getDefaultType());
        ks.load(null);
        ks.setCertificateEntry("caCert", caCert);

        tmf.init(ks);

        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(null, tmf.getTrustManagers(), null);

        return sslContext;
    }

    private HostnameVerifier getHostnameVerifier() {
        return (hostname, sslSession) -> true;
    }
}

package com.paytm.acquirer.netc.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;

import javax.validation.constraints.NotBlank;

@Data
@Configuration
@ConfigurationProperties(prefix = "netc.keystore")
public class DigitalSignatureProperties {
    /**
     * Location to a .pfx file where keys & certificates are stored.
     */
    @NotBlank
    private Resource keyStoreLocation;

    /**
     * KeyStore file format. Eg. PKCS12 (Standard)
     */
    @NotBlank
    private String keyStoreType;

    /**
     * KeyStore password.
     */
    @NotBlank
    private String keyStorePassword;

    /**
     * Alias of Paytm's keyStore entry.
     * i.e. name under which paytm's private key and certificates are stored
     */
    @NotBlank
    private String paytmKeyAlias;
    
    /**
     *Location of NPCI public key
     */
    private String npciPublicKeyLocation;
}

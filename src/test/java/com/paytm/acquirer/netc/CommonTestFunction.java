package com.paytm.acquirer.netc;

import com.paytm.acquirer.netc.config.properties.DigitalSignatureProperties;
import com.paytm.acquirer.netc.service.common.SignatureService;
import lombok.experimental.UtilityClass;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

@UtilityClass
public class CommonTestFunction {
  
  public static final String MAX_RETRY = "3";
  public static final String DUMMY_TAG_ID = "34161FA820328E400D4464E0";
  public static final String DUMMY_MSG_ID = "MSG00691655767793949077414P950";
  public static final String DUMMY_MSG_ID_MAX_RETRY = "MSG04691655767793949077414P950";
  public static final String DUMMY_TRANSACTION_ID = "P123456000000";
  
  public static SignatureService getSignatureService() {
    ResourceLoader resourceLoader = new DefaultResourceLoader();
    Resource resource = resourceLoader.getResource("classpath:UAT/NPCI_NEW_UAT.pfx");
    
    DigitalSignatureProperties digitalSignatureProperties = new DigitalSignatureProperties();
    digitalSignatureProperties.setKeyStoreLocation(resource);
    digitalSignatureProperties.setPaytmKeyAlias("75dfb0beb6844d94a96b901d2d2edcaa");
    digitalSignatureProperties.setKeyStoreType("PKCS12");
    digitalSignatureProperties.setKeyStorePassword("12345");
    digitalSignatureProperties.setNpciPublicKeyLocation("classpath:UAT/NPCI_SIGNING.pem");
    return new SignatureService(digitalSignatureProperties);
  }
}

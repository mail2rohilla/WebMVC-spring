package com.paytm.acquirer.netc.service.common;

import com.paytm.acquirer.netc.config.properties.EfkonDigitalSignatureProperties;
import com.paytm.acquirer.netc.dto.getException.ExceptionFileXml;
import com.paytm.acquirer.netc.exception.NetcEngineException;
import com.paytm.acquirer.netc.util.Constants;
import com.paytm.acquirer.netc.util.SignatureUtil;
import com.paytm.acquirer.netc.util.XmlUtil;
import com.paytm.transport.logger.Logger;
import com.paytm.transport.logger.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

import javax.xml.crypto.MarshalException;
import javax.xml.crypto.dsig.SignedInfo;
import javax.xml.crypto.dsig.XMLSignature;
import javax.xml.crypto.dsig.XMLSignatureException;
import javax.xml.crypto.dsig.XMLSignatureFactory;
import javax.xml.crypto.dsig.dom.DOMSignContext;
import javax.xml.crypto.dsig.dom.DOMValidateContext;
import javax.xml.crypto.dsig.keyinfo.KeyInfo;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.security.*;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Base64;

@Service
public class EfkonSignatureService implements ISignatureService {
  private static final Logger log = LoggerFactory.getLogger(EfkonSignatureService.class);

  private final PrivateKey signingPrivateKey;
  private final X509Certificate signingCertificate;
  private final XMLSignatureFactory signatureFactory;

  @Value("#{environment.acceptsProfiles('prod') ? 'true' : 'false' }")
  private Boolean validateXml;

  public EfkonSignatureService(EfkonDigitalSignatureProperties digitalSignatureProperties) {
    KeyStore keyStore = SignatureUtil.loadKeyStore(
      digitalSignatureProperties.getKeyStoreLocation(),
      digitalSignatureProperties.getKeyStoreType(),
      digitalSignatureProperties.getKeyStorePassword()
    );

    log.debug("Keystore location : {}, storetype : {}, password : {}, key alias : {}",
      digitalSignatureProperties.getKeyStoreLocation(), digitalSignatureProperties.getKeyStoreType(),
      digitalSignatureProperties.getKeyStorePassword().substring(0,
        1) + "_" + digitalSignatureProperties.getKeyStorePassword().substring(
        digitalSignatureProperties.getKeyStorePassword().length() - 1) + "_" + digitalSignatureProperties.getKeyStorePassword().length(),
      digitalSignatureProperties.getPaytmKeyAlias());


    KeyStore.PrivateKeyEntry keyEntry = SignatureUtil.getPrivateKeyEntry(
      keyStore,
      digitalSignatureProperties.getPaytmKeyAlias(),
      digitalSignatureProperties.getKeyStorePassword()
    );

    signingPrivateKey = keyEntry.getPrivateKey();
    signingCertificate = (X509Certificate) keyEntry.getCertificate();
    signatureFactory = XMLSignatureFactory.getInstance();
  }

  /**
   * Digitally Sign the XML document and embed the Signing info
   *
   * @param serializedXml XML document serialized as string
   * @return Signed XML document serialized as string
   */
  @Override
  public String signXmlDocument(String serializedXml) {
    // Instantiating the document to be signed
    Document xmlDoc = XmlUtil.createXmlDocument(serializedXml);

    // Creating a signing context
    DOMSignContext signContext = new DOMSignContext(signingPrivateKey, xmlDoc.getDocumentElement());

    // Assembling the XML Signature
    XMLSignature xmlSignature = generateXmlSignature();

    // Sign the document
    try {
      xmlSignature.sign(signContext);
    } catch (MarshalException | XMLSignatureException ex) {
      throw new NetcEngineException("Error while signing the efkon XML.", ex);
    }

    // return serialized signed document
    return XmlUtil.serializeXmlDocument(xmlDoc);
  }

  /**
   * Validate the digital signature of given xml
   *
   * @param serializedXml XML document serialized as string
   * @return validation result
   */
  @Override
  public boolean isXmlValid(String serializedXml) {
    if (!validateXml) return true;
    Document xmlDoc = XmlUtil.createXmlDocument(serializedXml);

    return validateXmlDoc(xmlDoc);
  }

  /**
   * @return {@code XMLSignature} object with available keys and certificate
   */
  private XMLSignature generateXmlSignature() {
    SignedInfo signedInfo = SignatureUtil.generateSignedInfo(signatureFactory);

    KeyInfo keyInfo = SignatureUtil.generateKeyInfo(signatureFactory, signingCertificate);

    return signatureFactory.newXMLSignature(signedInfo, keyInfo);
  }

  /**
   * Validate the Signature node inside the given document
   *
   * @param xmlDoc XML Document
   * @return validation result
   */
  private boolean validateXmlDoc(Document xmlDoc) {
    NodeList signatureNodes = xmlDoc.getElementsByTagNameNS(XMLSignature.XMLNS, "Signature");
    if (signatureNodes.getLength() < 1) {
      log.warn("Didn't found any Signature nodes while validating XML.");
      return false;
    }

    DOMValidateContext validateContext = new DOMValidateContext(new SignatureUtil.X509KeySelector(),
      signatureNodes.item(0));

    try {
      XMLSignature signature = signatureFactory.unmarshalXMLSignature(validateContext);

      return signature.validate(validateContext);
    } catch (MarshalException | XMLSignatureException ex) {
      throw new NetcEngineException("Error while validating signature.", ex);
    }
  }

  @Override
  public boolean signAndVerifyXmlDoc(ExceptionFileXml exceptionFileXml) {
    if (!validateXml) return true;
    try {
      Signature sign = Signature.getInstance("SHA256withRSA");
      sign.initVerify(getCertificateFromFile(exceptionFileXml.getCertificate()).getPublicKey());
      sign.update(Base64.getMimeDecoder().decode(exceptionFileXml.getOrgContent().replaceAll(Constants.REGEX_NEW_LINE, "")));
      byte[] signdDecode = Base64.getMimeDecoder().
        decode(exceptionFileXml.getSignature().replaceAll(Constants.REGEX_NEW_LINE, ""));
      boolean b = sign.verify(signdDecode);

      log.info("is valid data {}", b);
      return b;
    } catch (NoSuchAlgorithmException | SignatureException e) {
      log.error("Signature exception while verifying the sign {} ", e);
    } catch (InvalidKeyException e) {
      log.error("Invalid key exception while verifying the signature");
    }
    return false;
  }

  @Override
  public X509Certificate getCertificateFromFile(String certNodeData) {

    byte[] encodedCert = Base64.getDecoder().decode(certNodeData.replaceAll(Constants.REGEX_NEW_LINE, ""));
    try (ByteArrayInputStream inputStream = new ByteArrayInputStream(encodedCert)) {

      CertificateFactory certFactory = CertificateFactory.getInstance("X.509");
      return (X509Certificate) certFactory.generateCertificate(inputStream);
    }
    catch (CertificateException | IOException e) {
      log.error("Exception occurred while fetching efkon certificate from file", e);
    }
    return null;
  }

}

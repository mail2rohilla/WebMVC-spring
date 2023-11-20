package com.paytm.acquirer.netc.util;

import com.paytm.acquirer.netc.exception.NetcEngineException;
import com.paytm.transport.logger.Logger;
import com.paytm.transport.logger.LoggerFactory;
import lombok.experimental.UtilityClass;
import org.springframework.core.io.Resource;
import org.springframework.util.Assert;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.xml.crypto.AlgorithmMethod;
import javax.xml.crypto.KeySelector;
import javax.xml.crypto.KeySelectorException;
import javax.xml.crypto.KeySelectorResult;
import javax.xml.crypto.XMLCryptoContext;
import javax.xml.crypto.XMLStructure;
import javax.xml.crypto.dsig.CanonicalizationMethod;
import javax.xml.crypto.dsig.DigestMethod;
import javax.xml.crypto.dsig.Reference;
import javax.xml.crypto.dsig.SignedInfo;
import javax.xml.crypto.dsig.Transform;
import javax.xml.crypto.dsig.XMLSignatureFactory;
import javax.xml.crypto.dsig.keyinfo.KeyInfo;
import javax.xml.crypto.dsig.keyinfo.KeyInfoFactory;
import javax.xml.crypto.dsig.keyinfo.X509Data;
import javax.xml.crypto.dsig.spec.C14NMethodParameterSpec;
import javax.xml.crypto.dsig.spec.TransformParameterSpec;
import java.io.IOException;
import java.security.InvalidAlgorithmParameterException;
import java.security.KeyStore;
import java.security.KeyStore.PasswordProtection;
import java.security.KeyStore.PrivateKeyEntry;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.UnrecoverableEntryException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@UtilityClass
public class SignatureUtil {
  private static final Logger log = LoggerFactory.getLogger(SignatureUtil.class);

  private static final String RSA_SHA_256 = "http://www.w3.org/2001/04/xmldsig-more#rsa-sha256";
  private static final String DSA_SHA_256 = "http://www.w3.org/2009/xmldsig11#dsa-sha256";

  public static KeyStore loadKeyStore(@NotBlank Resource keyStoreLocation,
                                      @NotBlank String keyStoreType,
                                      @NotBlank String keyStorePassword) {
    Assert.hasLength(keyStoreType, "KeyStore type is empty.");
    Assert.hasLength(keyStorePassword, "KeyStore password is empty.");

    try {
      KeyStore keyStore = KeyStore.getInstance(keyStoreType);
      keyStore.load(keyStoreLocation.getInputStream(), keyStorePassword.toCharArray());

      return keyStore;
    } catch (KeyStoreException kse) {
      throw new NetcEngineException(String.format("Invalid keyStore file type: %s", keyStoreType), kse);
    } catch (NoSuchAlgorithmException | CertificateException | IOException ex) {
      throw new NetcEngineException("Unable to open keyStore with given configs.", ex);
    }
  }

  public static PrivateKeyEntry getPrivateKeyEntry(@NotNull KeyStore keyStore,
                                                   @NotBlank String keyAlias,
                                                   @NotBlank String keyPassword) {
    Assert.notNull(keyStore, "KeyStore is null.");
    Assert.hasLength(keyAlias, "KeyEntry name is empty.");
    Assert.hasLength(keyPassword, "KetEntry password is empty.");

    try {
      PasswordProtection passwordProtection = new PasswordProtection(keyPassword.toCharArray());
      return (PrivateKeyEntry) keyStore.getEntry(keyAlias, passwordProtection);
    } catch (NoSuchAlgorithmException | UnrecoverableEntryException | KeyStoreException ex) {
      throw new NetcEngineException(
        String.format("Failed to load entry from keyStore with alias: %s and given password.",
          keyAlias), ex);
    }
  }


  /**
   * Generate {@code SignedInfo} object with signature method RSA-SHA256
   *
   * @param signatureFactory DOM signature factory
   * @return Final SignedInfo object that is going to be signed
   */
  public static SignedInfo generateSignedInfo(@NotNull XMLSignatureFactory signatureFactory) {
    Assert.notNull(signatureFactory, "Given XMLSignatureFactory is null.");

    try {
      Reference ref = signatureFactory.newReference(
        "", signatureFactory.newDigestMethod(DigestMethod.SHA256, null),
        Collections.singletonList(signatureFactory.newTransform(Transform.ENVELOPED,
          (TransformParameterSpec) null)), null, null
      );

      return signatureFactory.newSignedInfo(
        signatureFactory.newCanonicalizationMethod(CanonicalizationMethod.INCLUSIVE,
          (C14NMethodParameterSpec) null),
        signatureFactory.newSignatureMethod(RSA_SHA_256, null),
        Collections.singletonList(ref)
      );
    }
    catch (NoSuchAlgorithmException | InvalidAlgorithmParameterException ex) {
      log.error("Error while generating KeyInfo with certificate info.", ex);
      throw new NetcEngineException("Error while generating KeyInfo with certificate info.", ex);
    }
  }


  /**
   * Generate {@code KeyInfo} object
   * which contains information that enables the recipient
   * to find the key needed to validate the signature
   *
   * @return keyInfo with certificate info
   */
  public static KeyInfo generateKeyInfo(@NotNull XMLSignatureFactory signatureFactory,
                                        @NotNull X509Certificate signingCertificate) {
    Assert.notNull(signatureFactory, "XMLSignatureFactory is null.");
    Assert.notNull(signingCertificate, "signingCertificate is null.");

    KeyInfoFactory keyInfoFactory = signatureFactory.getKeyInfoFactory();
    List<Object> x509Content = new ArrayList<>();

    x509Content.add(signingCertificate.getSubjectX500Principal().getName());
    x509Content.add(signingCertificate);

    return keyInfoFactory.newKeyInfo(Collections.singletonList(
      keyInfoFactory.newX509Data(x509Content)
    ));
  }

  /**
   * A very simple implementation of {@code KeySelector} that returns the public key from
   * the first X.509 certificate it finds in the {@code X509Data}
   *
   * @Note A more complete X.509 key selector implementation would check other types of X509Data
   * and establish trust in the validation key by using a keystore of trusted keys,
   * or by finding and validating a certificate chain from a trust anchor to the certificate
   * containing the public key.
   * @Ref https://www.oracle.com/technical-resources/articles/javase/dig-signature-api.html
   */
  public static class X509KeySelector extends KeySelector {
    public KeySelectorResult select(KeyInfo keyInfo,
                                    Purpose purpose,
                                    AlgorithmMethod method,
                                    XMLCryptoContext context)
      throws KeySelectorException {

      for (Object value : keyInfo.getContent()) {
        XMLStructure info = (XMLStructure) value;
        if (!(info instanceof X509Data)) continue;
        X509Data x509Data = (X509Data) info;

        for (Object o : x509Data.getContent()) {
          if (!(o instanceof X509Certificate)) continue;

          final PublicKey key = ((X509Certificate) o).getPublicKey();
          // Make sure the algorithm is compatible with the method.
          if (algEquals(method.getAlgorithm(), key.getAlgorithm())) {
            return () -> key;
          }
        }
      }
      throw new KeySelectorException("No KeyValue element found!");
    }

    static boolean algEquals(String algURI, String algName) {
      if (algName.equalsIgnoreCase("DSA") &&
        algURI.equalsIgnoreCase(DSA_SHA_256)) {
        return true;
      } else if (algName.equalsIgnoreCase("RSA") &&
        algURI.equalsIgnoreCase(RSA_SHA_256)) {
        return true;
      } else {
        return false;
      }
    }
  }
}

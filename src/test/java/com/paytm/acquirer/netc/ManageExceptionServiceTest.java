package com.paytm.acquirer.netc;

import com.paytm.acquirer.netc.config.properties.EfkonDigitalSignatureProperties;
import com.paytm.acquirer.netc.db.repositories.master.ErrorCodeMappingMasterRepository;
import com.paytm.acquirer.netc.dto.common.HeaderXml;
import com.paytm.acquirer.netc.dto.efkon.TagUpdateResponse;
import com.paytm.acquirer.netc.dto.manageException.ReqMngExceptionDto;
import com.paytm.acquirer.netc.dto.manageException.RespMngExceptionDto;
import com.paytm.acquirer.netc.service.ManageExceptionService;
import com.paytm.acquirer.netc.service.common.EfkonSignatureService;
import com.paytm.acquirer.netc.service.common.MetadataService;
import com.paytm.acquirer.netc.service.common.NetcClient;
import com.paytm.acquirer.netc.service.common.SignatureService;
import com.paytm.acquirer.netc.util.FactoryMethodService;
import com.paytm.acquirer.netc.util.JsonUtil;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.test.util.ReflectionTestUtils;

import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@RunWith(MockitoJUnitRunner.class)
public class ManageExceptionServiceTest {

  final String response = "{\"result\":\"SUCCESS\",\"respCode\":\"000\",\"totalRequestCount\":1,\"successRequestCount\":0,\"tagEntries\":[{\"operation\":\"ADD\",\"tagId\":\"34161FA820328D720EEC5D80\",\"result\":\"SUCCESS\",\"errCode\":\"000\",\"errCodeMapping\":\"Success\"},{\"operation\":\"REMOVE\",\"tagId\":\"34161FA820328D720EEC5D81\",\"result\":\"SUCCESS\",\"errCode\":\"000\",\"errCodeMapping\":\"Success\"},{\"operation\":\"ADD\",\"tagId\":\"34161FA820328D720EEC5D82\",\"result\":\"FAILURE\",\"errCode\":\"000\",\"errCodeMapping\":\"Success\"}]}";

  final String xmlResponse = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
    "<etc:RespMngException xmlns:etc=\"http://npci.org/etc/schema/\">\n" +
    "    <Head msgId=\"MSG00521627885793441198153M441\" orgId=\"PAYM\" ts=\"2021-08-02T11:59:53\" ver=\"1.0\"/>\n" +
    "    <Txn id=\"521627885793441198153M\" note=\"\" orgTxnId=\"\" refId=\"\" refUrl=\"\" ts=\"2021-08-02T11:59:53\" type=\"ManageException\">\n" +
    "        <Resp respCode=\"000\" result=\"SUCCESS\" sucessReqCnt=\"1\" totReqCnt=\"1\" ts=\"2021-08-02T11:59:53\">\n" +
    "            <Tag errCode=\"000\" op=\"ADD\" result=\"SUCCESS\" seqNum=\"1\" tagId=\"34161FA82032C61403CEA5C0\"/>\n" +
    "        </Resp>\n" +
    "    </Txn>\n" +
    "    <Signature xmlns=\"http://www.w3.org/2000/09/xmldsig#\">\n" +
    "        <SignedInfo>\n" +
    "            <CanonicalizationMethod Algorithm=\"http://www.w3.org/TR/2001/REC-xml-c14n-20010315\"/>\n" +
    "            <SignatureMethod Algorithm=\"http://www.w3.org/2001/04/xmldsig-more#rsa-sha256\"/>\n" +
    "            <Reference URI=\"\">\n" +
    "                <Transforms>\n" +
    "                    <Transform Algorithm=\"http://www.w3.org/2000/09/xmldsig#enveloped-signature\"/>\n" +
    "                </Transforms>\n" +
    "                <DigestMethod Algorithm=\"http://www.w3.org/2001/04/xmlenc#sha256\"/>\n" +
    "                <DigestValue>LkaK9EX96Xf9qb5j4B+TXYmVAe963UiAPJjizJGA0ds=</DigestValue>\n" +
    "            </Reference>\n" +
    "        </SignedInfo>\n" +
    "        <SignatureValue>R3iL1+3LNwCniklW062c5rB2WGuHyhteDA+yL8apA+t8gBlWHDbfuyptTyqo4US0CH2kN/0MsCn4&#13;\n" +
    "doE32G20FINilFSkKd3OfnSFXkT0gM76w3UeUUKlymPT6gD9JyVWsdz3rt6nqr2/7tr+TsuaPWMD&#13;\n" +
    "0iZXz4EYnHWjcCDoLS0OIxjLScpTR/gqKwUGAsSYW1SaLISxW0RWdeLflu7kfGrnZyvASqgr1R8u&#13;\n" +
    "4bH+8XN040+s01nu1Bo5ibvJLA/C/Sj0ZdmnP2aFAAyY6UQr0z+K7Cq3psV9ejuMqGKGGZMttkP4&#13;\n" +
    "ndK4pI5WjkrlJmFpTEK+VPLDeIfvZNuDcCY+lw==</SignatureValue>\n" +
    "        <KeyInfo>\n" +
    "            <X509Data>\n" +
    "                <X509SubjectName>CN=netcsigning.npci.org.in,O=National Payments Corporation of India,L=Chennai,ST=Tamil Nadu,C=IN,2.5.4.5=#1306313839303637,1.3.6.1.4.1.311.60.2.1.3=#1302494e,2.5.4.15=#0c1450726976617465204f7267616e697a6174696f6e</X509SubjectName>\n" +
    "                <X509Certificate>MIIHRDCCBiygAwIBAgIQB+YOBFKT+Ak4Ex6wx+IROTANBgkqhkiG9w0BAQsFADB1MQswCQYDVQQG&#13;\n" +
    "EwJVUzEVMBMGA1UEChMMRGlnaUNlcnQgSW5jMRkwFwYDVQQLExB3d3cuZGlnaWNlcnQuY29tMTQw&#13;\n" +
    "MgYDVQQDEytEaWdpQ2VydCBTSEEyIEV4dGVuZGVkIFZhbGlkYXRpb24gU2VydmVyIENBMB4XDTIw&#13;\n" +
    "MDgxNDAwMDAwMFoXDTIyMDgxOTEyMDAwMFowgcwxHTAbBgNVBA8MFFByaXZhdGUgT3JnYW5pemF0&#13;\n" +
    "aW9uMRMwEQYLKwYBBAGCNzwCAQMTAklOMQ8wDQYDVQQFEwYxODkwNjcxCzAJBgNVBAYTAklOMRMw&#13;\n" +
    "EQYDVQQIEwpUYW1pbCBOYWR1MRAwDgYDVQQHEwdDaGVubmFpMS8wLQYDVQQKEyZOYXRpb25hbCBQ&#13;\n" +
    "YXltZW50cyBDb3Jwb3JhdGlvbiBvZiBJbmRpYTEgMB4GA1UEAxMXbmV0Y3NpZ25pbmcubnBjaS5v&#13;\n" +
    "cmcuaW4wggEiMA0GCSqGSIb3DQEBAQUAA4IBDwAwggEKAoIBAQCxva70hLE7oigT9nz04GobUTBd&#13;\n" +
    "2AuQBs7yyijgqpdVea8jTdJFFdtM4ktpMEsZwjTLmqVbdSpi1Wa1uSxkfOqpm6m4dW9LZ7sK0hSN&#13;\n" +
    "yfn7br0mv9wfpjUfju1e+YglsR+B8ATgyF6H9wBkhCorWVF8RTZXpWva/IapNiKsHEyaeMNrzfRj&#13;\n" +
    "QHqC735oApVAI+cqv3GjKVLlYq2EhRGtvyVBYH9ddwcSE4XDBQr92E8+FkfLIMxwyqbfubrA0rIC&#13;\n" +
    "zY5RZuwlreBBTNHkzIGJoYfVtdG/STFOMcU3esDxY519x6tM8l+owV+bRiUaZbaHSGiBm6lFxtgj&#13;\n" +
    "OFnz0VC4Ib2XAgMBAAGjggN2MIIDcjAfBgNVHSMEGDAWgBQ901Cl1qCt7vNKYApl0yHU+PjWDzAd&#13;\n" +
    "BgNVHQ4EFgQURXYUv2mj/7SkJ+ui6qTXHAdYcAgwIgYDVR0RBBswGYIXbmV0Y3NpZ25pbmcubnBj&#13;\n" +
    "aS5vcmcuaW4wDgYDVR0PAQH/BAQDAgWgMB0GA1UdJQQWMBQGCCsGAQUFBwMBBggrBgEFBQcDAjB1&#13;\n" +
    "BgNVHR8EbjBsMDSgMqAwhi5odHRwOi8vY3JsMy5kaWdpY2VydC5jb20vc2hhMi1ldi1zZXJ2ZXIt&#13;\n" +
    "ZzIuY3JsMDSgMqAwhi5odHRwOi8vY3JsNC5kaWdpY2VydC5jb20vc2hhMi1ldi1zZXJ2ZXItZzIu&#13;\n" +
    "Y3JsMEsGA1UdIAREMEIwNwYJYIZIAYb9bAIBMCowKAYIKwYBBQUHAgEWHGh0dHBzOi8vd3d3LmRp&#13;\n" +
    "Z2ljZXJ0LmNvbS9DUFMwBwYFZ4EMAQEwgYgGCCsGAQUFBwEBBHwwejAkBggrBgEFBQcwAYYYaHR0&#13;\n" +
    "cDovL29jc3AuZGlnaWNlcnQuY29tMFIGCCsGAQUFBzAChkZodHRwOi8vY2FjZXJ0cy5kaWdpY2Vy&#13;\n" +
    "dC5jb20vRGlnaUNlcnRTSEEyRXh0ZW5kZWRWYWxpZGF0aW9uU2VydmVyQ0EuY3J0MAwGA1UdEwEB&#13;\n" +
    "/wQCMAAwggF+BgorBgEEAdZ5AgQCBIIBbgSCAWoBaAB2ACl5vvCeOTkh8FZzn2Old+W+V32cYAr4&#13;\n" +
    "+U1dJlwlXceEAAABc+z4xVoAAAQDAEcwRQIhAJTEbbtdFiPc7Y2/uMfKdbJ6Z+bNmZiGM3+pAGEh&#13;\n" +
    "LyQCAiBiiYdpBW5vc0nyCnLXoUHtZFm+oHJdoS6Rwkw2cvI75AB2AEHIyrHfIkZKEMahOglCh15O&#13;\n" +
    "MYsbA+vrS8do8JBilgb2AAABc+z4xSoAAAQDAEcwRQIhAM8r0GHh39RC1nMGLuYF4k4k/NfzyMex&#13;\n" +
    "B+c9nPx5bPHsAiArxWpbphVNojwQ9WChLeFfYmiVkZmvBcN2h3tsE2drwgB2AEalVet1+pEgMLWi&#13;\n" +
    "iWn0830RLEF0vv1JuIWr8vxw/m1HAAABc+z4xbYAAAQDAEcwRQIhAN4wHmz1hTVQT5CH8aXqv4dV&#13;\n" +
    "+CL7KcJ0HnonTz1mW5qwAiBvIYHP1H6I3JwnH//m6Gq6EcoJp1TzXJUrGRLjQA8V0zANBgkqhkiG&#13;\n" +
    "9w0BAQsFAAOCAQEADsZg7Xdorx5P+cqvq23/aY2wjvz6umgw9wbDKBD6SFf6xJVe/cFipAOdAZ9Z&#13;\n" +
    "UeVrauWYs/yOOCBVOYoTxbojMgeZzXuOnPGeSMNkxJnVr5qhHLtT9fnl1r3Bz2D366Z8kLKPXhfv&#13;\n" +
    "ZrnwT80gliHlRSezvikaKIXJfJg6tmAzR0886qJTjLAapd5U1fl7cjTG702C1ZdWNo/TXl9/P6ky&#13;\n" +
    "BLQhwBQqoyxy8ydKrxNqmuqYAjdGl5am2+q7kG7z/SSsoCKfFRc2uNn4s9AwaQ0kW8R/RDMKioiF&#13;\n" +
    "GSyfguoOIDJGjO10b7YbGk8kGUqpEsBV384iMT9mdznz2VVERqaeiA==</X509Certificate>\n" +
    "            </X509Data>\n" +
    "        </KeyInfo>\n" +
    "    </Signature>\n" +
    "</etc:RespMngException>";

  @Mock
  private NetcClient netcClient;
  @Mock
  private SignatureService signatureService;
  @Mock
  private ErrorCodeMappingMasterRepository errorCodeMappingMasterRepository;
  @Mock
  private FactoryMethodService factoryMethodService;

  @InjectMocks
  MetadataService metadataService;
  @InjectMocks
  private ManageExceptionService manageExceptionService;

  @Before
  public void setUp() {
    ReflectionTestUtils.setField(manageExceptionService, "metadataService", metadataService);
    ReflectionTestUtils.setField(metadataService, "orgId", "TEST-ORG");
  }

  @Test
  public void efkonResponseTest() {

    RespMngExceptionDto respMngExceptionDto = JsonUtil.parseJson(response, RespMngExceptionDto.class);
    List<RespMngExceptionDto> respMngExceptionDtos = Collections.singletonList(respMngExceptionDto);
    List<TagUpdateResponse> tagUpdateResponses = new ArrayList<>();

    SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    final String lastUpdatedTime = dateFormat.format(Timestamp.valueOf(
      LocalDateTime.now()));

    respMngExceptionDtos.forEach(reqMngExceptionObj -> {
      reqMngExceptionObj.getTagEntries().forEach(tagEntry -> {

        TagUpdateResponse tagUpdateResponse = TagUpdateResponse.builder()
          .result(tagEntry.getResult())
          .blacklistUpdatedAt(lastUpdatedTime)
          .tagId(tagEntry.getTagId())
          .build();
        tagUpdateResponses.add(tagUpdateResponse);
      });

    });

    Assert.assertEquals(3, tagUpdateResponses.size());
    Assert.assertEquals("SUCCESS", tagUpdateResponses.get(0).getResult());
  }


  @Test
  public void updateEfkonTagsInExceptionListTest() {

    ResourceLoader resourceLoader = new DefaultResourceLoader();
    Resource resource = resourceLoader.getResource("classpath:UAT/NPCI_NEW_UAT.pfx");

    EfkonDigitalSignatureProperties digitalSignatureProperties = new EfkonDigitalSignatureProperties();
    digitalSignatureProperties.setKeyStoreLocation(resource);
    digitalSignatureProperties.setPaytmKeyAlias("75dfb0beb6844d94a96b901d2d2edcaa");
    digitalSignatureProperties.setKeyStoreType("PKCS12");
    digitalSignatureProperties.setKeyStorePassword("12345");

    Mockito.when(factoryMethodService.getInstance(Mockito.anyString())).thenReturn(
      new EfkonSignatureService(digitalSignatureProperties));
    Mockito.when(netcClient.requestManageException(Mockito.anyString())).thenReturn(xmlResponse);
    List<TagUpdateResponse> tagUpdateResponseList = manageExceptionService.updateEfkonTagsInExceptionList(
      prepareReqMngException());

    Assert.assertEquals("SUCCESS", tagUpdateResponseList.get(0).getResult());
  }

  public HeaderXml prepareHeaderXml() {
    HeaderXml headerXml = new HeaderXml();
    headerXml.setOrganizationId("PAYM");
    headerXml.setMessageId("MSG12345");
    headerXml.setVersion("25");
    headerXml.setTimeStamp(Timestamp.valueOf(LocalDateTime.now()).toString());
    return headerXml;
  }

  public ReqMngExceptionDto prepareReqMngException() {

    ReqMngExceptionDto reqMngExceptionDto = new ReqMngExceptionDto();
    reqMngExceptionDto.setOrgId("PAYM");

    List<ReqMngExceptionDto.TagEntry> tagEntries = new ArrayList<>();

    ReqMngExceptionDto.TagEntry tagEntry_1 = new ReqMngExceptionDto.TagEntry();
    tagEntry_1.setExceptionCode("03");
    tagEntry_1.setOperation("ADD");
    tagEntry_1.setTagId("3416PRAD2021593517497937");


    ReqMngExceptionDto.TagEntry tagEntry_2 = new ReqMngExceptionDto.TagEntry();
    tagEntry_1.setExceptionCode("03");
    tagEntry_1.setOperation("ADD");
    tagEntry_1.setTagId("3416PRAD2021593517497936");

    tagEntries.add(tagEntry_1);
    tagEntries.add(tagEntry_2);

    reqMngExceptionDto.setTagEntryTagList(tagEntries);
    return reqMngExceptionDto;
  }
}

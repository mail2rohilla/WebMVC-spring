package com.paytm.acquirer.netc.service.temp;

import com.paytm.acquirer.netc.util.Utils;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class MockService {

  public String syncTimeRequestMock() {
    String currentTime = Utils.getFormattedDate(LocalDateTime.now());
    return "<?xml version=\"1.0\" encoding=\"UTF-8\"?><etc:RespSyncTime xmlns:etc=\"http://npci.org/etc/schema/\">\n" +
      "    <Head msgId=\"941559\" orgId=\"PAYM\" ts=\"" + currentTime + "\" ver=\"1.0\"/>\n" +
      "    <Resp respCode=\"000\" result=\"SUCCESS\" ts=\"" + currentTime + "\">\n" +
      "        <Time serverTime=\"" + currentTime + "\"/>\n" +
      "    </Resp>\n" +
      "</etc:RespSyncTime>";
  }

  public String requestDetailMock() {
    return "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\"?><etc:RespDetails xmlns:etc=\"http://npci.org/etc/schema/\"><Head msgId=\"MSG00156448588338852697aM\" orgId=\"SRIV\" ts=\"2022-01-11T16:14:05\" ver=\"1.0\"/><Txn id=\"00000000000000000314\" note=\"ABC00000000000000314@123445\" orgTxnId=\"ABC00000000000000316\" refId=\"ABC00000000000000315\" refUrl=\"abc@xyz\" ts=\"2022-01-11T16:14:05\" type=\"FETCH\"><Resp respCode=\"000\" result=\"SUCCESS\" successReqCnt=\"1\" totReqCnt=\"1\" ts=\"2022-01-11T16:14:05\"><Vehicle errCode=\"000\"><VehicleDetails><Detail name=\"VEHICLECLASS\" value=\"VC6\"/><Detail name=\"TAGSTATUS\" value=\"A\"/><Detail name=\"ISSUEDATE\" value=\"2018-12-12\"/><Detail name=\"EXCCODE\" value=\"00\"/><Detail name=\"BANKID\" value=\"607529\"/><Detail name=\"REGNUMBER\" value=\"HR5BS2260\"/><Detail name=\"TAGID\" value=\"E20034120134FC000CCCF999\"/><Detail name=\"COMVEHICLE\" value=\"F\"/><Detail name=\"TID\" value=\"E29934120134FC000CCCF9999\"/></VehicleDetails></Vehicle></Resp></Txn><Signature xmlns=\"http://www.w3.org/2000/09/xmldsig#\"><SignedInfo><CanonicalizationMethod Algorithm=\"http://www.w3.org/TR/2001/REC-xml-c14n-20010315\"/><SignatureMethod Algorithm=\"http://www.w3.org/2001/04/xmldsig-more#rsa-sha256\"/><Reference URI=\"\"><Transforms><Transform Algorithm=\"http://www.w3.org/2000/09/xmldsig#enveloped-signature\"/></Transforms><DigestMethod Algorithm=\"http://www.w3.org/2001/04/xmlenc#sha256\"/><DigestValue>CrSa3HzlZgITQjHm+syTF7Y8wSic1BLkcSgnoWMr8FY=</DigestValue></Reference></SignedInfo><SignatureValue>AKa926rDIjfrEkNSfbT4QnWtYLVAPjTTBBVcSAAaa/z0knqrtxDaErUlV2fds7wfJ4BJZgRaO6j3\n" +
      "eZHYJlTZUGWmiwRpTmHVfJ6g/dhn+6amPMS6ECIKLllNI45q29CdCLtdVn+ywotYe35AS0XWgb2V\n" +
      "4Zeus0nDJwhJTyNx313QxqZ6sHKItppFnLfVgB7gHn08rgl5RUuuxXjFYkCb4sul9nE7BnAA7Jsa\n" +
      "9rjP/so+9iNimG7pq3vUwKQwpZJ54MD7jSfMN9lWbuZHuJPYCmCsRmSnNVLueS/RWnAymyktOWT1\n" +
      "nfoYnKJVGlDbYk+yIMpGFw/SkFaabC3qcuEi6w==</SignatureValue><KeyInfo><X509Data><X509SubjectName>CN=netc-uat.paytm.com,O=One97 Communications Limited,L=Noida,ST=Uttar Pradesh,C=IN</X509SubjectName><X509Certificate>MIIHIjCCBgqgAwIBAgIQDc2bPRcBC8If34pOCuNLXzANBgkqhkiG9w0BAQsFADBNMQswCQYDVQQG\n" +
      "EwJVUzEVMBMGA1UEChMMRGlnaUNlcnQgSW5jMScwJQYDVQQDEx5EaWdpQ2VydCBTSEEyIFNlY3Vy\n" +
      "ZSBTZXJ2ZXIgQ0EwHhcNMTgwMzI4MDAwMDAwWhcNMjAwNjMwMDAwMDAwWjB5MQswCQYDVQQGEwJJ\n" +
      "TjEWMBQGA1UECBMNVXR0YXIgUHJhZGVzaDEOMAwGA1UEBxMFTm9pZGExJTAjBgNVBAoTHE9uZTk3\n" +
      "IENvbW11bmljYXRpb25zIExpbWl0ZWQxGzAZBgNVBAMTEm5ldGMtdWF0LnBheXRtLmNvbTCCASIw\n" +
      "DQYJKoZIhvcNAQEBBQADggEPADCCAQoCggEBAJS/31I5LXeoJCsLNLEYevfvYT2+/ZdaeasLn1JW\n" +
      "VnZoTRdtyFsz/4rtmEf7kTmdMUp8ur5z6kgboJvDrSzbEYw4dSpLbcbeRQ//2814Ay/oenYmeXis\n" +
      "q1A7HUcNgLB+k8u5CdM+OOwEk4qetMnaXb5M+d7IGWf0mAuctr2jfN1jIml2xh3hCQpfKRVEYaWd\n" +
      "TmM90MwyDaK8aH1GdfIJiWngL2WQW6nWhu5d9Yf3kxmwMd5+4zDcV/AB8aFm5sSooZrf7h4ENE3s\n" +
      "RBMFmCWfoUR6LU7/C6gbqWEOrYD4/mkR1WWL2QuAcF3n0O2UHzW9d17899915CYIcNLSbr9AsKcC\n" +
      "AwEAAaOCA9AwggPMMB8GA1UdIwQYMBaAFA+AYRyCMWHVLyjnjUY4tCzhxtniMB0GA1UdDgQWBBRC\n" +
      "eryYzDMYNmtrjZqGZmQOtWMiVTAdBgNVHREEFjAUghJuZXRjLXVhdC5wYXl0bS5jb20wDgYDVR0P\n" +
      "AQH/BAQDAgWgMB0GA1UdJQQWMBQGCCsGAQUFBwMBBggrBgEFBQcDAjBrBgNVHR8EZDBiMC+gLaAr\n" +
      "hilodHRwOi8vY3JsMy5kaWdpY2VydC5jb20vc3NjYS1zaGEyLWc2LmNybDAvoC2gK4YpaHR0cDov\n" +
      "L2NybDQuZGlnaWNlcnQuY29tL3NzY2Etc2hhMi1nNi5jcmwwTAYDVR0gBEUwQzA3BglghkgBhv1s\n" +
      "AQEwKjAoBggrBgEFBQcCARYcaHR0cHM6Ly93d3cuZGlnaWNlcnQuY29tL0NQUzAIBgZngQwBAgIw\n" +
      "fAYIKwYBBQUHAQEEcDBuMCQGCCsGAQUFBzABhhhodHRwOi8vb2NzcC5kaWdpY2VydC5jb20wRgYI\n" +
      "KwYBBQUHMAKGOmh0dHA6Ly9jYWNlcnRzLmRpZ2ljZXJ0LmNvbS9EaWdpQ2VydFNIQTJTZWN1cmVT\n" +
      "ZXJ2ZXJDQS5jcnQwCQYDVR0TBAIwADCCAfYGCisGAQQB1nkCBAIEggHmBIIB4gHgAHYApLkJkLQY\n" +
      "WBSHuxOizGdwCjw1mAT5G9+443fNDsgN3BAAAAFibFGeCAAABAMARzBFAiBhgMf63e/FBWE+ddij\n" +
      "BKfgWrG5coSz5XjQp8e2UKqChAIhAO6yW+nOEepKPWKKfKp0SREMmC5A+0pR+4TKrPpekR87AHcA\n" +
      "b1N2rDHwMRnYmQCkURX/dxUcEdkCwQApBo2yCJo32RMAAAFibFGfOwAABAMASDBGAiEA0sjw7Jxj\n" +
      "aGP1y1fMA/PZPOoatXu31riGl85NBRMVYkACIQD0t+vUxoc5A+CkG23Ao4tMWXqA1z1MQBM6ordr\n" +
      "LfRRzgB1ALvZ37wfinG1k5Qjl6qSe0c4V5UKq1LoGpCWZDaOHtGFAAABYmxRnjUAAAQDAEYwRAIg\n" +
      "QK7PDlyu7xIq/2IBUBLZ7RGEGdRunfYcBGiW1SknZ50CIHBrnCrmamZ39pxEz+LysR3Jr80YOYtb\n" +
      "F4yFdkKk/VAUAHYAVYHUwhaQNgFK6gubVzxT8MDkOHhwJQgXL6OqHQcT0wwAAAFibFGglgAABAMA\n" +
      "RzBFAiA01j23Ewp5jKaOJZS8WwPqY2d/XHTEYaoMqt5VI7v7MwIhALJqAyJzH5iMt00EVdKRMycA\n" +
      "1879oj87Q/hC6i0xOV1tMA0GCSqGSIb3DQEBCwUAA4IBAQCMUlyD2h5y/4VRoA8OGIY22OUiuw+w\n" +
      "sucSkkwF1GF6vdy4rPYXi8urUQoqWijq4sY1DD+IxEenuhr/CqumdrmeecLoK+R5pDm4Gt6gCxQH\n" +
      "Kq911vUIL3Qxd3V6Jj0VdHGkyRCmVmGjze/WOq63xyydJWlQUioX+rQjfo/2sn/0vv84FxkVQogR\n" +
      "Rtgw1mm3mtMF2mLN9lyjGbGOktjKC54Y95ZYvkc9W2NWsLvDhOpNRaGiQp2GEn83J7qEiAbtbhKn\n" +
      "3y2TPg28A0rBpaYIHFAOTPA+wJFNNAmKim8BPFVJO6EajksMx+R+zgwWtvActuY+M92xzyf97kby\n" +
      "lwHn/xSu</X509Certificate></X509Data></KeyInfo></Signature></etc:RespDetails>";
  }


  public ResponseEntity<Void> reqAcceptedMock() {
     return ResponseEntity.status(HttpStatus.ACCEPTED).build();
  }

  public String checkTxnStatusMock() {
    return "<?xml version=\"1.0\" encoding=\"UTF-8\"?><etc:RespChkTxn xmlns:etc=\"http://npci.org/etc/schema/\">\n" +
      "    <Head msgId=\"MSG0093162440458544210622cT442\" orgId=\"PBAT\" ts=\"2021-12-06T17:44:00\" ver=\"1.0\"/>\n" +
      "    <Txn id=\"93162440458544210622cT\" note=\"\" orgTxnId=\"ORGTXN93162440458544210622cT\" refId=\"\" refUrl=\"\" ts=\"2021-12-06T17:44:00\" type=\"ChkTxn\">\n" +
      "        <Resp respCode=\"000\" result=\"SUCCESS\" sucessReqCnt=\"1\" totReqCnt=\"1\" ts=\"2021-12-06T17:44:00\">\n" +
      "            <TxnStatusReqList>\n" +
      "                <Status acquirerId=\"720424\" errCode=\"000\" merchantId=\"001002\" result=\"SUCCESS\" txnDate=\"2021-12-06\" txnId=\"33163879829626271510cP\">\n" +
      "                    <TxnList payeeErrCode=\"\" respCode=\"00\" txnReaderTime=\"2021-12-06T19:08:12\" txnReceivedTime=\"2021-12-06T19:09:12\" txnStatus=\"ACCEPTED\" txnType=\"DEBIT\"/>\n" +
      "                </Status>\n" +
      "            </TxnStatusReqList>\n" +
      "        </Resp>\n" +
      "    </Txn>\n" +
      "<Signature xmlns=\"http://www.w3.org/2000/09/xmldsig#\"><SignedInfo><CanonicalizationMethod Algorithm=\"http://www.w3.org/TR/2001/REC-xml-c14n-20010315\"/><SignatureMethod Algorithm=\"http://www.w3.org/2001/04/xmldsig-more#rsa-sha256\"/><Reference URI=\"\"><Transforms><Transform Algorithm=\"http://www.w3.org/2000/09/xmldsig#enveloped-signature\"/></Transforms><DigestMethod Algorithm=\"http://www.w3.org/2001/04/xmlenc#sha256\"/><DigestValue>+zj19ZLEhHQPcRsBZw/PRuY2xxABq6r2kJpO2GV2Fc4=</DigestValue></Reference></SignedInfo><SignatureValue>oL+LyTpkKgs0znFSjyTtscHmL64yzqpKxH478fk31Xo96FmVP2aIyhbAPcyEOUZa5a984qCxLG8F\n" +
      "BNFLiPiWwGzYSJFHTsbv8O4rEXiLi3611ekLsGe4Sys6ZeTLU6bDJZwhU/od3/M6aMDcX8ZcT9bH\n" +
      "b3nScDbReejUmYe+BJ5jj27qYo5px6oopyI0xQs308z+JYNgjUsFhLNu2nASD+agrG8mq7UGQeHw\n" +
      "CVG5dEt+ItNi+giwND9GfYkZtaHaHIeTV9bsmBzn6WkrPIqaXGlXF64WkuPxi2AWPgqX22vQLzz3\n" +
      "BDVAmElbsOSEksEFY3iKBVI1jtI8eua2hT1dUQ==</SignatureValue><KeyInfo><X509Data><X509SubjectName>CN=netcsigning.npci.org.in,O=National Payments Corporation of India,L=Chennai,ST=Tamil Nadu,C=IN,2.5.4.5=#1306313839303637,1.3.6.1.4.1.311.60.2.1.3=#1302494e,2.5.4.15=#0c1450726976617465204f7267616e697a6174696f6e</X509SubjectName><X509Certificate>MIIHRDCCBiygAwIBAgIQB+YOBFKT+Ak4Ex6wx+IROTANBgkqhkiG9w0BAQsFADB1MQswCQYDVQQG\n" +
      "EwJVUzEVMBMGA1UEChMMRGlnaUNlcnQgSW5jMRkwFwYDVQQLExB3d3cuZGlnaWNlcnQuY29tMTQw\n" +
      "MgYDVQQDEytEaWdpQ2VydCBTSEEyIEV4dGVuZGVkIFZhbGlkYXRpb24gU2VydmVyIENBMB4XDTIw\n" +
      "MDgxNDAwMDAwMFoXDTIyMDgxOTEyMDAwMFowgcwxHTAbBgNVBA8MFFByaXZhdGUgT3JnYW5pemF0\n" +
      "aW9uMRMwEQYLKwYBBAGCNzwCAQMTAklOMQ8wDQYDVQQFEwYxODkwNjcxCzAJBgNVBAYTAklOMRMw\n" +
      "EQYDVQQIEwpUYW1pbCBOYWR1MRAwDgYDVQQHEwdDaGVubmFpMS8wLQYDVQQKEyZOYXRpb25hbCBQ\n" +
      "YXltZW50cyBDb3Jwb3JhdGlvbiBvZiBJbmRpYTEgMB4GA1UEAxMXbmV0Y3NpZ25pbmcubnBjaS5v\n" +
      "cmcuaW4wggEiMA0GCSqGSIb3DQEBAQUAA4IBDwAwggEKAoIBAQCxva70hLE7oigT9nz04GobUTBd\n" +
      "2AuQBs7yyijgqpdVea8jTdJFFdtM4ktpMEsZwjTLmqVbdSpi1Wa1uSxkfOqpm6m4dW9LZ7sK0hSN\n" +
      "yfn7br0mv9wfpjUfju1e+YglsR+B8ATgyF6H9wBkhCorWVF8RTZXpWva/IapNiKsHEyaeMNrzfRj\n" +
      "QHqC735oApVAI+cqv3GjKVLlYq2EhRGtvyVBYH9ddwcSE4XDBQr92E8+FkfLIMxwyqbfubrA0rIC\n" +
      "zY5RZuwlreBBTNHkzIGJoYfVtdG/STFOMcU3esDxY519x6tM8l+owV+bRiUaZbaHSGiBm6lFxtgj\n" +
      "OFnz0VC4Ib2XAgMBAAGjggN2MIIDcjAfBgNVHSMEGDAWgBQ901Cl1qCt7vNKYApl0yHU+PjWDzAd\n" +
      "BgNVHQ4EFgQURXYUv2mj/7SkJ+ui6qTXHAdYcAgwIgYDVR0RBBswGYIXbmV0Y3NpZ25pbmcubnBj\n" +
      "aS5vcmcuaW4wDgYDVR0PAQH/BAQDAgWgMB0GA1UdJQQWMBQGCCsGAQUFBwMBBggrBgEFBQcDAjB1\n" +
      "BgNVHR8EbjBsMDSgMqAwhi5odHRwOi8vY3JsMy5kaWdpY2VydC5jb20vc2hhMi1ldi1zZXJ2ZXIt\n" +
      "ZzIuY3JsMDSgMqAwhi5odHRwOi8vY3JsNC5kaWdpY2VydC5jb20vc2hhMi1ldi1zZXJ2ZXItZzIu\n" +
      "Y3JsMEsGA1UdIAREMEIwNwYJYIZIAYb9bAIBMCowKAYIKwYBBQUHAgEWHGh0dHBzOi8vd3d3LmRp\n" +
      "Z2ljZXJ0LmNvbS9DUFMwBwYFZ4EMAQEwgYgGCCsGAQUFBwEBBHwwejAkBggrBgEFBQcwAYYYaHR0\n" +
      "cDovL29jc3AuZGlnaWNlcnQuY29tMFIGCCsGAQUFBzAChkZodHRwOi8vY2FjZXJ0cy5kaWdpY2Vy\n" +
      "dC5jb20vRGlnaUNlcnRTSEEyRXh0ZW5kZWRWYWxpZGF0aW9uU2VydmVyQ0EuY3J0MAwGA1UdEwEB\n" +
      "/wQCMAAwggF+BgorBgEEAdZ5AgQCBIIBbgSCAWoBaAB2ACl5vvCeOTkh8FZzn2Old+W+V32cYAr4\n" +
      "+U1dJlwlXceEAAABc+z4xVoAAAQDAEcwRQIhAJTEbbtdFiPc7Y2/uMfKdbJ6Z+bNmZiGM3+pAGEh\n" +
      "LyQCAiBiiYdpBW5vc0nyCnLXoUHtZFm+oHJdoS6Rwkw2cvI75AB2AEHIyrHfIkZKEMahOglCh15O\n" +
      "MYsbA+vrS8do8JBilgb2AAABc+z4xSoAAAQDAEcwRQIhAM8r0GHh39RC1nMGLuYF4k4k/NfzyMex\n" +
      "B+c9nPx5bPHsAiArxWpbphVNojwQ9WChLeFfYmiVkZmvBcN2h3tsE2drwgB2AEalVet1+pEgMLWi\n" +
      "iWn0830RLEF0vv1JuIWr8vxw/m1HAAABc+z4xbYAAAQDAEcwRQIhAN4wHmz1hTVQT5CH8aXqv4dV\n" +
      "+CL7KcJ0HnonTz1mW5qwAiBvIYHP1H6I3JwnH//m6Gq6EcoJp1TzXJUrGRLjQA8V0zANBgkqhkiG\n" +
      "9w0BAQsFAAOCAQEADsZg7Xdorx5P+cqvq23/aY2wjvz6umgw9wbDKBD6SFf6xJVe/cFipAOdAZ9Z\n" +
      "UeVrauWYs/yOOCBVOYoTxbojMgeZzXuOnPGeSMNkxJnVr5qhHLtT9fnl1r3Bz2D366Z8kLKPXhfv\n" +
      "ZrnwT80gliHlRSezvikaKIXJfJg6tmAzR0886qJTjLAapd5U1fl7cjTG702C1ZdWNo/TXl9/P6ky\n" +
      "BLQhwBQqoyxy8ydKrxNqmuqYAjdGl5am2+q7kG7z/SSsoCKfFRc2uNn4s9AwaQ0kW8R/RDMKioiF\n" +
      "GSyfguoOIDJGjO10b7YbGk8kGUqpEsBV384iMT9mdznz2VVERqaeiA==</X509Certificate></X509Data></KeyInfo></Signature></etc:RespChkTxn>";
  }

  public String manageExceptionMock() {
    return "";
  }
}

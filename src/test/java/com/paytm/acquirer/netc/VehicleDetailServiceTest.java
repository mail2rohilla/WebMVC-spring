package com.paytm.acquirer.netc;

import com.paytm.acquirer.netc.config.properties.RetryProperties;
import com.paytm.acquirer.netc.db.repositories.master.ErrorCodeMappingMasterRepository;
import com.paytm.acquirer.netc.dto.common.HeaderXml;
import com.paytm.acquirer.netc.dto.common.VehicleDetailsXml;
import com.paytm.acquirer.netc.dto.common.VehicleResponseXml;
import com.paytm.acquirer.netc.dto.common.VehicleTxnResponseXml;
import com.paytm.acquirer.netc.dto.details.ReqDetails;
import com.paytm.acquirer.netc.dto.details.RespDetails;
import com.paytm.acquirer.netc.dto.details.RespDetailsXml;
import com.paytm.acquirer.netc.exception.NetcEngineException;
import com.paytm.acquirer.netc.service.ReqDetailsService;
import com.paytm.acquirer.netc.service.TimeService;
import com.paytm.acquirer.netc.service.common.MetadataService;
import com.paytm.acquirer.netc.service.common.NetcClient;
import com.paytm.acquirer.netc.service.common.RestTemplateService;
import com.paytm.acquirer.netc.service.common.SignatureService;
import com.paytm.acquirer.netc.service.retry.WrappedNetcClient;
import com.paytm.acquirer.netc.util.XmlUtil;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.paytm.acquirer.netc.util.Constants.SUCCESS_RESPONSE_CODE;
import static com.paytm.acquirer.netc.util.Constants.TIME_SYNC_ERROR_CODE;
import static com.paytm.acquirer.netc.util.Constants.VehicleDetails.BANK_ID;
import static com.paytm.acquirer.netc.util.Constants.VehicleDetails.EXEMPTION_CODE;
import static com.paytm.acquirer.netc.util.Constants.VehicleDetails.REG_NO;
import static com.paytm.acquirer.netc.util.Constants.VehicleDetails.TAG_ID;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Matchers.any;

@RunWith(MockitoJUnitRunner.class)
public class VehicleDetailServiceTest {

  @Mock
  private ErrorCodeMappingMasterRepository mappingRepository;
  @Mock
  private TimeService timeService;
  @Mock
  private RetryProperties retryProperties;
  @Mock
  private RestTemplateService restTemplateService;
  @Mock
  private CircuitBreaker circuitBreaker;

  @InjectMocks
  private NetcClient netcClient;
  @InjectMocks
  private WrappedNetcClient wrappedNetcClient;
  @InjectMocks
  private MetadataService metadataService;
  @InjectMocks
  private ReqDetailsService reqDetailService;
  
  @Rule
  public ExpectedException exceptionRule = ExpectedException.none();
  
  private static final String dummyTagId = "34161FA820328E400D4464E0";
  private static final String dummyMsgId = "MSG00521627885793441198153M441";
  SignatureService signatureService = CommonTestFunction.getSignatureService();

  @Before
  public void setUp() {
    ReflectionTestUtils.setField(netcClient, "restTemplateService", restTemplateService);
    ReflectionTestUtils.setField(wrappedNetcClient, "netcClient", netcClient);
    ReflectionTestUtils.setField(metadataService, "orgId", "TEST-ORG");
    ReflectionTestUtils.setField(reqDetailService, "wrappedNetcClient", wrappedNetcClient);
    ReflectionTestUtils.setField(reqDetailService, "signatureService", signatureService);
    ReflectionTestUtils.setField(reqDetailService, "metadataService", metadataService);
    Mockito.when(retryProperties.getTimeSyncMaxRetry()).thenCallRealMethod();
  }

  @Test
  public void reqDetailServiceSuccessTest () {
    TestingConditions conditions = new TestingConditions();
    Mockito.when(restTemplateService.executePostRequest(anyString(), eq(String.class), anyString(),
      any(), eq(null))).thenReturn(XmlUtil.serializeXmlDocument(getRespDetailsXml(conditions)));
    RespDetails respDetails = reqDetailService.requestDetailsWithRetry(getReqDetails(), "TEST");
    Assert.assertNotNull(respDetails);
    Assert.assertEquals(SUCCESS_RESPONSE_CODE, respDetails.getRespCode());
  }

  @Test
  public void reqDetailServiceSuccessRetryTest () {
    TestingConditions conditions = new TestingConditions();
    conditions.setTimeSyncError(true);
    Mockito.when(restTemplateService.executePostRequest(anyString(), eq(String.class), anyString(),
      any(), eq(null))).thenReturn(XmlUtil.serializeXmlDocument(getRespDetailsXml(conditions)));
    RespDetails respDetails = reqDetailService.requestDetailsWithRetry(getReqDetails(), "TEST");
    Assert.assertNotNull(respDetails);
    Assert.assertEquals(TIME_SYNC_ERROR_CODE, respDetails.getRespCode());
  }
  
  @Test
  public void reqDetailServiceUnabletoParse () {
    Mockito.when(restTemplateService.executePostRequest(anyString(), eq(String.class), anyString(),
      any(), eq(null))).thenReturn(getInvalidXml());
    exceptionRule.expect(NetcEngineException.class);
    reqDetailService.requestDetailsWithRetry(getReqDetails(), "TEST");
  }
  
  private String getInvalidXml() {
    return "<etc:RespDetails xlns:etc=\"http://npci.org/etc/schema/\"><Head msgId=\"MSG00521627885793441198153M441\"/><Txn><Resp respCode=\"205\" totReqCnt=\"1\" successReqCnt=\"1\"><Vehicle errCode=\"000\"><VehicleDetails><Detail name=\"REGNUMBER\" value=\"BR1234\"/><Detail name=\"TAGID\" value=\"34161FA820328E400D4464E0\"/><Detail name=\"BANKID\" value=\"1234\"/><Detail name=\"EXCCODE\" value=\"00\"/></VehicleDetails></Vehicle></Resp></Txn></etc:RespDetails>";
  }
  
  private ReqDetails getReqDetails() {
    ReqDetails reqDetails = new ReqDetails();
    reqDetails.setTagId(dummyTagId);
    return  reqDetails;
  }

  private RespDetailsXml getRespDetailsXml(TestingConditions conditions ) {
    RespDetailsXml respDetails = new RespDetailsXml();
    HeaderXml headerXml = new HeaderXml();
    headerXml.setMessageId(dummyMsgId);

    VehicleTxnResponseXml vehicleTxnResponseXml = new VehicleTxnResponseXml();
    VehicleResponseXml vehicleResponseXml = new VehicleResponseXml();
    VehicleResponseXml.VehicleXml vehicleXml = new VehicleResponseXml.VehicleXml();
    List<VehicleDetailsXml> vehicleDetailsXmlList = new ArrayList<>();
    VehicleDetailsXml vehicleDetailsXml = new VehicleDetailsXml();

    
    Set<VehicleDetailsXml.Detail> details = new HashSet<>();
    details.add(new VehicleDetailsXml.Detail( TAG_ID, dummyTagId));
    details.add(new VehicleDetailsXml.Detail( EXEMPTION_CODE, "00"));
    details.add(new VehicleDetailsXml.Detail( BANK_ID, "1234"));
    details.add(new VehicleDetailsXml.Detail( REG_NO, "BR1234"));

    vehicleDetailsXml.setDetails(details);
    vehicleDetailsXmlList.add(vehicleDetailsXml);

    vehicleXml.setVehicleDetailsList(vehicleDetailsXmlList);
    vehicleXml.setErrorCode(SUCCESS_RESPONSE_CODE);
    vehicleResponseXml.setVehicle(vehicleXml);
    vehicleResponseXml.setResponseCode(SUCCESS_RESPONSE_CODE);
    if(conditions.isTimeSyncError())
      vehicleResponseXml.setResponseCode(TIME_SYNC_ERROR_CODE);

    vehicleResponseXml.setTotalRequestCount(1);
    vehicleResponseXml.setSuccessRequestCount(1);
    vehicleTxnResponseXml.setResponse(vehicleResponseXml);
    respDetails.setTransaction(vehicleTxnResponseXml);
    respDetails.setHeader(headerXml);
    return  respDetails;
  }
  
}

package com.paytm.acquirer.netc.service;

import com.google.common.collect.Iterables;
import com.paytm.acquirer.netc.adapter.ExceptionAdapter;
import com.paytm.acquirer.netc.db.entities.ErrorCodeMapping;
import com.paytm.acquirer.netc.db.repositories.master.ErrorCodeMappingMasterRepository;
import com.paytm.acquirer.netc.dto.efkon.TagUpdateResponse;
import com.paytm.acquirer.netc.dto.manageException.ReqMngExceptionDto;
import com.paytm.acquirer.netc.dto.manageException.ReqMngExceptionXml;
import com.paytm.acquirer.netc.dto.manageException.RespMngExceptionDto;
import com.paytm.acquirer.netc.dto.manageException.RespMngExceptionXml;
import com.paytm.acquirer.netc.service.common.ISignatureService;
import com.paytm.acquirer.netc.service.common.MetadataService;
import com.paytm.acquirer.netc.service.common.NetcClient;
import com.paytm.acquirer.netc.service.common.SignatureService;
import com.paytm.acquirer.netc.util.Constants;
import com.paytm.acquirer.netc.util.FactoryMethodService;
import com.paytm.acquirer.netc.util.Utils;
import com.paytm.acquirer.netc.util.XmlUtil;
import com.paytm.transport.logger.Logger;
import com.paytm.transport.logger.LoggerFactory;
import com.paytm.transport.util.DynamicPropertyUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ManageExceptionService {
  private static final Logger log = LoggerFactory.getLogger(ManageExceptionService.class);
  private final NetcClient netcClient;
  private final SignatureService signatureService;
  private final MetadataService metadataService;
  private final ErrorCodeMappingMasterRepository errorCodeMappingMasterRepository;
  private final FactoryMethodService factoryMethodService;


  public RespMngExceptionDto updateTagsInExceptionList(ReqMngExceptionDto reqMngExceptionDto) {
    // create common timeStamp
    String requestTimeStamp = Utils.getFormattedDate();

    ReqMngExceptionXml request = ExceptionAdapter.convert(reqMngExceptionDto, requestTimeStamp, metadataService);
    request.setHeader(metadataService.createXmlHeader(request.getTransaction().getId(), requestTimeStamp, null));
    isCustomOrgIdPresentCheckAndSet(request, reqMngExceptionDto.getOrgId());

    String signedXml = signatureService.signXmlDocument(XmlUtil.serializeXmlDocument(request));

    log.info("Signed Request XML for ReqMngException : {}", signedXml);
    String response = netcClient.requestManageException(signedXml);
    log.info("Signed Response XML for ReqMngException : {}", response);

    RespMngExceptionXml respMngExceptionXml = XmlUtil.deserializeXmlDocument(response, RespMngExceptionXml.class);

    postProcess(respMngExceptionXml);

    return ExceptionAdapter.convert(respMngExceptionXml, getErrorCodeMapping());
  }

  public List<TagUpdateResponse> updateEfkonTagsInExceptionList(ReqMngExceptionDto reqMngExceptionDto) {
    // create common timeStamp
    List<RespMngExceptionDto> respMngExceptionDtos = new ArrayList<>();
    AtomicBoolean isCustomOrgId = new AtomicBoolean(false);
    Iterables.partition(reqMngExceptionDto.getTagEntryTagList(),
      DynamicPropertyUtil.getIntPropertyValue("TAG_ENTRY_SIZE", 50))
      .forEach(tagEntries -> {
        ReqMngExceptionDto reqMngExceptionDtoBatch = new ReqMngExceptionDto();
        reqMngExceptionDtoBatch.setTagEntryTagList(tagEntries);
        String requestTimeStamp = Utils.getFormattedDate();

        ReqMngExceptionXml request = ExceptionAdapter.convert(reqMngExceptionDto, requestTimeStamp, metadataService);
        request.setHeader(metadataService.createXmlHeader(request.getTransaction().getId(), requestTimeStamp, null));
        isCustomOrgId.set(isCustomOrgIdPresentCheckAndSet(request, reqMngExceptionDto.getOrgId()));

        ISignatureService iSignatureService = factoryMethodService.getInstance(getInstanceKey(isCustomOrgId.get()));
        String signedXml = iSignatureService.signXmlDocument(XmlUtil.serializeXmlDocument(request));

        log.info("Signed Request XML for ReqMngException : {}", signedXml);
        String response = netcClient.requestManageException(signedXml);
        log.info("Signed Response XML for ReqMngException : {}", response);

        RespMngExceptionXml respMngExceptionXml = XmlUtil.deserializeXmlDocument(response, RespMngExceptionXml.class);

        postProcess(respMngExceptionXml);

        RespMngExceptionDto respMngExceptionDto = ExceptionAdapter.convert(respMngExceptionXml, getErrorCodeMapping());
        respMngExceptionDtos.add(respMngExceptionDto);
      });

    return prepareTagUpdateResponse(respMngExceptionDtos, isCustomOrgId.get());
  }

  private List<TagUpdateResponse> prepareTagUpdateResponse(List<RespMngExceptionDto> respMngExceptionDtos, boolean isCustom) {
    List<TagUpdateResponse> tagUpdateResponses = new ArrayList<>();

    SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    final String lastUpdatedTime = dateFormat.format(Timestamp.valueOf(
      LocalDateTime.now()));

    respMngExceptionDtos.stream().forEach(reqMngExceptionObj ->
      reqMngExceptionObj.getTagEntries().stream().forEach(tagEntry -> {

        TagUpdateResponse tagUpdateResponse = TagUpdateResponse.builder()
          .result(tagEntry.getResult())
          .blacklistUpdatedAt(lastUpdatedTime)
          .tagId(tagEntry.getTagId())
          .build();
        tagUpdateResponses.add(tagUpdateResponse);
      })
    );

    List<TagUpdateResponse> filteredTagResponses = tagUpdateResponses.stream().filter(
      tagUpdateResponse -> tagUpdateResponse.getResult().equalsIgnoreCase(Constants.RESULT_SUCCESS)).collect(
      Collectors.toList());

    if (!isCustom && !CollectionUtils.isEmpty(filteredTagResponses)) {
      netcClient.sendTagDetailsToExceptionHandler(filteredTagResponses);
    }

    return tagUpdateResponses;
  }

  private String getInstanceKey(boolean isCustomOrgId) {
    if (isCustomOrgId) return "EFKON";
    return "TAS";
  }

  private boolean isCustomOrgIdPresentCheckAndSet(ReqMngExceptionXml request, String orgId) {
    if (StringUtils.hasText(orgId)) {
      request.getHeader().setOrganizationId(orgId);
      return true;
    }
    return false;
  }

  private void postProcess(RespMngExceptionXml xml) {
    if (CollectionUtils.isEmpty(xml.getTransaction().getResponse().getTags())) {
      return;
    }
    xml.getTransaction().getResponse().getTags().forEach(data -> {
      if (data.getResult().equalsIgnoreCase(Constants.RESULT_FAILURE) &&
        ((data.getErrorCode().equalsIgnoreCase("155") &&
          data.getOperation().equalsIgnoreCase("ADD")) ||
        (data.getErrorCode().equalsIgnoreCase("157") &&
          data.getOperation().equalsIgnoreCase("REMOVE")))
      ) {
        data.setErrorCode("000");
        data.setResult(Constants.RESULT_SUCCESS);
      }
    });
    if (xml.getTransaction().getResponse().getTags().stream()
        .allMatch(t -> t.getResult().equalsIgnoreCase(Constants.RESULT_SUCCESS))) {
      xml.getTransaction().getResponse().setResult(Constants.RESULT_SUCCESS);
      xml.getTransaction().getResponse().setResponseCode("000");
    }
  }

  // TODO: cache it later
  private Map<String, String> getErrorCodeMapping() {
    List<ErrorCodeMapping> errorCodeMappings = errorCodeMappingMasterRepository.findAll();

    return errorCodeMappings.stream()
        .collect(Collectors.toMap(ErrorCodeMapping::getErrorCode, ErrorCodeMapping::getMapping));
  }
}

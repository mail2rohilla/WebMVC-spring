package com.paytm.acquirer.netc.controller;

import com.paytm.acquirer.netc.dto.details.ReqDetails;
import com.paytm.acquirer.netc.dto.details.RespDetails;
import com.paytm.acquirer.netc.dto.efkon.TagUpdateResponse;
import com.paytm.acquirer.netc.dto.kafka.ExceptionMetaInfo;
import com.paytm.acquirer.netc.dto.listParticipant.NetcParticipantListRequest;
import com.paytm.acquirer.netc.dto.listParticipant.RespListParticipant;
import com.paytm.acquirer.netc.dto.manageException.ReqMngExceptionDto;
import com.paytm.acquirer.netc.dto.manageException.RespMngExceptionDto;
import com.paytm.acquirer.netc.dto.queryException.ReqQueryExceptionDto;
import com.paytm.acquirer.netc.dto.syncTime.TimeSyncResponse;
import com.paytm.acquirer.netc.enums.ErrorMessage;
import com.paytm.acquirer.netc.exception.NetcEngineException;
import com.paytm.acquirer.netc.service.DiffService;
import com.paytm.acquirer.netc.service.InitService;
import com.paytm.acquirer.netc.service.InitSftpService;
import com.paytm.acquirer.netc.service.ListParticipantService;
import com.paytm.acquirer.netc.service.ManageExceptionService;
import com.paytm.acquirer.netc.service.ReqDetailsService;
import com.paytm.acquirer.netc.service.TagExceptionService;
import com.paytm.acquirer.netc.service.TimeService;
import com.paytm.acquirer.netc.service.common.MetadataService;
import com.paytm.acquirer.netc.service.common.RedisService;
import com.paytm.transport.logger.Logger;
import com.paytm.transport.logger.LoggerFactory;
import com.paytm.transport.metrics.Monitor;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static com.paytm.transport.metrics.Monitor.ServiceGroup.API_IN;

@RestController
@RequiredArgsConstructor
@RequestMapping(value = "sync", produces = MediaType.APPLICATION_JSON_VALUE, consumes = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "SyncApi", description = "Controller to receive all the sync requests")
public class SyncApisController {
    private static final Logger log = LoggerFactory.getLogger(SyncApisController.class);

    private final ReqDetailsService reqDetailsService;
    private final TagExceptionService exceptionService;
    private final ManageExceptionService manageExceptionService;
    private final TimeService timeService;
    private final MetadataService metadataService;
    private final RedisService redisService;
    private final DiffService diffService;
    private final InitService initService;
    private final InitSftpService initSftpService;
    private final ListParticipantService listParticipantService;

    @PostMapping("reqDetails")
    @Monitor(name = "requestDetails", metricGroup = API_IN)
    @Operation(summary = "Request Vehicle Details", description = "Used to fetch vehicle details from NETC in realtime")
    @ApiResponse(
      responseCode = "202",
      description = "Accepted",
      content = @Content(
        mediaType = "application/json",
        schema = @Schema(implementation = RespDetails.class),
        examples = {
          @ExampleObject(value = "{\"respCode\":\"000\",\"errCode\":\"000\",\"errCodeMapping\":\"Success\",\"result\":\"SUCCESS\",\"vehicleDetails\":[{\"tagId\":\"34161FA820328EE8110DDFE0\",\"regNumber\":\"MC2EBKRC0MH495170\",\"tid\":\"E2801105200074510E820A69\",\"vehicleClass\":\"VC5\",\"tagStatus\":\"A\",\"issueDate\":\"2021-10-08\",\"exceptionCodes\":[\"00\"],\"bankId\":\"608116\",\"commercialVehicle\":\"T\"}]}")
        }
      )
    )
    @ApiResponse(
      responseCode = "4xx",
      description = "Failed in api validation",
      content = @Content(
      mediaType = "application/json")
    )
    @ApiResponse(
      responseCode = "5xx",
      description = "Service Unavailable Or Internal Server Error",
      content = @Content(
        mediaType = "application/json")
    )
    public RespDetails requestDetails(@Parameter(description = "Object to pass TagId/TID/RegNo.")
                                      @RequestBody ReqDetails reqDetails, @RequestParam(value = "msgId", required = false) String msgId) {
        return reqDetailsService.requestDetailsWithRetry(reqDetails, msgId);
    }

    @PostMapping("reqGetExceptionList")
    @Monitor(name = "INIT_request", metricGroup = API_IN)
    @Operation(summary = "INIT Api Endpoint", description = "API to initiate the init for all toll plazas.")
    @ApiResponse(
      responseCode = "202",
      description = "Accepted",
      content = @Content(
        mediaType = "text/plain")
    )
    @ApiResponse(
      responseCode = "4xx",
      description = "Failed in api validation",
      content = @Content(
        mediaType = "text/plain")
    )
    @ApiResponse(
      responseCode = "5xx",
      description = "Service Unavailable Or Internal Server Error",
      content = @Content(
        mediaType = "text/plain")
    )
    public void requestGetExceptionList() {
        if (redisService.isInitInProgress()) {
            log.info("No new INIT request is allowed while INIT is already in progress");
            return;
        }
        redisService.removeInitCompletionFlag();
        initService.getExceptionList(0);
    }

    @PostMapping("reqGetExceptionListPlazaWise")
    @Monitor(name = "INIT_plazaWise_request", metricGroup = API_IN)
    @Operation(summary = "INIT Api Endpoint plazawise", description = "API to initiate the INIT for the selected plazas.")
    @ApiResponse(
      responseCode = "202",
      description = "Accepted",
      content = @Content(
        mediaType = "text/plain")
    )
    @ApiResponse(
      responseCode = "4xx",
      description = "Failed in api validation",
      content = @Content(
        mediaType = "text/plain")
    )
    @ApiResponse(
      responseCode = "5xx",
      description = "Service Unavailable Or Internal Server Error",
      content = @Content(
        mediaType = "text/plain")
    )
    public void requestGetExceptionListPlazaWise(@Parameter(description = "Object to pass Plaza IDs") @RequestBody ExceptionMetaInfo additionalParam) {
        if (redisService.isInitInProgress()) {
            log.info("No new INIT request is allowed while INIT is already in progress");
            throw new NetcEngineException(ErrorMessage.INIT_IN_PROGRESS);
        }
        redisService.removeInitCompletionFlag();
        initService.getExceptionList(0, additionalParam);
    }

    @PostMapping("reqQueryExceptionList")
    @Monitor(name = "DIFF_request", metricGroup = API_IN)
    @Operation(summary = "DIFF API endpoint", description = "API to request the diff data from NPCI.")
    @ApiResponse(
      responseCode = "202",
      description = "Accepted",
      content = @Content(
        mediaType = "text/plain")
    )
    @ApiResponse(
      responseCode = "4xx",
      description = "Failed in api validation",
      content = @Content(
        mediaType = "text/plain")
    )
    @ApiResponse(
      responseCode = "5xx",
      description = "Service Unavailable Or Internal Server Error",
      content = @Content(
        mediaType = "text/plain")
    )
    public void requestQueryExceptionList(@Parameter(description = "Object to pass Diff Request Parameters") @RequestBody ReqQueryExceptionDto reqQueryExceptionDto) {
        exceptionService.queryExceptionList(0, reqQueryExceptionDto);
    }

    @PostMapping("reqExceptionTagDiffFiles")
    @Monitor(name = "DIFF_SFTP_request", metricGroup = API_IN)
    @Operation(summary = "Fetch the Diff data from NPCI SFTP", description = "Used to fetch diff data from NPCI SFTP server")
    @ApiResponse(
      responseCode = "202",
      description = "Accepted",
      content = @Content(
        mediaType = "text/plain")
    )
    @ApiResponse(
      responseCode = "4xx",
      description = "Failed in api validation",
      content = @Content(
        mediaType = "text/plain")
    )
    @ApiResponse(
      responseCode = "5xx",
      description = "Service Unavailable Or Internal Server Error",
      content = @Content(
        mediaType = "text/plain")
    )
    public void requestProcessExceptionDiffFile(@Parameter(description = "Object to pass Request Exception Diff Parameters") @RequestBody ReqQueryExceptionDto reqQueryExceptionDto) {
        diffService.fetchExceptionDiffFilesTag(reqQueryExceptionDto);
    }
    
    @PostMapping("reqMngException")
    @Monitor(name = "reqMngException", metricGroup = API_IN)
    @Operation(summary = "Update Statuses of the Tags", description = "Used to Add/Remove tags in exception list.")
    @ApiResponse(
      responseCode = "202",
      description = "Accepted",
      content = @Content(
        mediaType = "application/json",
        schema = @Schema(implementation = RespMngExceptionDto.class),
        examples = {
          @ExampleObject(value = "{\"result\":\"SUCCESS\",\"respCode\":\"000\",\"totalRequestCount\":1,\"successRequestCount\":0,\"tagEntries\":[{\"operation\":\"ADD\",\"tagId\":\"34161FA82026C81C02504580\",\"result\":\"SUCCESS\",\"errCode\":\"000\",\"errCodeMapping\":\"Success\"}]}")
        }
      )
    )
    @ApiResponse(
      responseCode = "4xx",
      description = "Failed in api validation",
      content = @Content(
        mediaType = "application/json")
    )
    @ApiResponse(
      responseCode = "5xx",
      description = "Service Unavailable Or Internal Server Error",
      content = @Content(
        mediaType = "application/json")
    )
    public RespMngExceptionDto requestManageException(@Parameter(description = "Object to pass ID and details to update tags") @RequestBody ReqMngExceptionDto reqMngExceptionDto) {
        return manageExceptionService.updateTagsInExceptionList(reqMngExceptionDto);
    }

    @PostMapping("timeSync")
    @Monitor(name = "timeSync", metricGroup = API_IN)
    @Operation(summary = "Sync Time", description = "Fetch and sync netc time")
    @ApiResponse(
      responseCode = "202",
      description = "Accepted",
      content = @Content(
        mediaType = "application/json",
        schema = @Schema(implementation = TimeSyncResponse.class),
        examples = {
          @ExampleObject(value = "{\"localTime\":\"2022-12-26T16:23:50\",\"serverTime\":\"2022-12-26T16:23:50\",\"tasLocalServerTime\":\"2022-12-26T16:23:50\"}")
        }
      )
    )
    @ApiResponse(
      responseCode = "4xx",
      description = "Failed in api validation",
      content = @Content(
        mediaType = "application/json")
    )
    @ApiResponse(
      responseCode = "5xx",
      description = "Service Unavailable Or Internal Server Error",
      content = @Content(
        mediaType = "application/json")
    )
    public TimeSyncResponse syncTime() {
        return timeService.syncTime();
    }

    @PostMapping("updateCounter")
    @Monitor(name = "updateCounter", metricGroup = API_IN)
    @Hidden
    @Operation(summary = "Update Counter for Plaza", description = "Reset plaza counter to zero for all plaza")
    @ApiResponse(
      responseCode = "202",
      description = "Accepted",
      content = @Content(
        mediaType = "text/plain")
    )
    @ApiResponse(
      responseCode = "4xx",
      description = "Failed in api validation",
      content = @Content(
        mediaType = "text/plain")
    )
    @ApiResponse(
      responseCode = "5xx",
      description = "Service Unavailable Or Internal Server Error",
      content = @Content(
        mediaType = "text/plain")
    )
    public void updateCounter() {
        metadataService.updateCounter();
    }

    @PostMapping("reqExceptionTagFiles")
    @Monitor(name = "INIT_SFTP_request", metricGroup = API_IN)
    @Hidden
    @Operation(summary = "Api endoint to get init data from NPCI SFTP", description = "Fetch complete exception data from NPIC SFTP server.")
    @ApiResponse(
      responseCode = "202",
      description = "Accepted",
      content = @Content(
        mediaType = "text/plain")
    )
    @ApiResponse(
      responseCode = "4xx",
      description = "Failed in api validation",
      content = @Content(
        mediaType = "text/plain")
    )
    @ApiResponse(
      responseCode = "5xx",
      description = "Service Unavailable Or Internal Server Error",
      content = @Content(
        mediaType = "text/plain")
    )
    public void requestProcessExceptionTagFile(@Parameter(description = "Flag to enable reset") @RequestParam(value = "resetFlag", defaultValue = "false") boolean resetFlag) {
        initSftpService.pollExceptionFilesTag(resetFlag);
    }

    @PostMapping("efkonReqMngException")
    @Monitor(name = "efkonReqMngException", metricGroup = API_IN)
    @Hidden
    @Operation(summary = "Update Efkon Tags In ExceptionList", description = "Add/Remove tags in exception list. Used By Issuer/Acquirer.")
    @ApiResponse(
      responseCode = "202",
      description = "Accepted",
      content = @Content(
        mediaType = "application/json")
    )
    @ApiResponse(
      responseCode = "4xx",
      description = "Failed in api validation",
      content = @Content(
        mediaType = "application/json")
    )
    @ApiResponse(
      responseCode = "5xx",
      description = "Service Unavailable Or Internal Server Error",
      content = @Content(
        mediaType = "application/json")
    )
    public List<TagUpdateResponse> requestManageExceptionEfkon(@Parameter(description = "Object to pass ID to update tags") @RequestBody ReqMngExceptionDto reqMngExceptionDto) {
        return manageExceptionService.updateEfkonTagsInExceptionList(reqMngExceptionDto);
    }
    
    @PostMapping("reqListParticipant")
    @ApiOperation("Trigger fetching ALL Participant bank data from NPCI")
    @Monitor(name = "LIST_IIN_Request", metricGroup = API_IN)
    public void requestListParticipant(@RequestParam(value = "msgId", required = false) String msgId) {
        listParticipantService.requestListParticipantRetry(msgId);
    }
    
    @PostMapping("requestIssuerParticipant")
    @ApiOperation("Fetch Active Participant bank data of the given issuerIin")
    @Monitor(name = "requestIssuerParticipant", metricGroup = API_IN)
    public RespListParticipant requestIssuerParticipant(@RequestBody NetcParticipantListRequest netcParticipantListRequest) {
       return listParticipantService.requestIssuerParticipant(netcParticipantListRequest.getIssuerIin());
    }
}

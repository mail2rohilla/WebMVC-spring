package com.paytm.acquirer.netc.config.properties;

import com.paytm.acquirer.netc.util.Constants;
import com.paytm.transport.util.DynamicPropertyUtil;
import lombok.Data;
import org.springframework.stereotype.Component;

@Data
@Component
public class TimeoutProperties {
    public Integer getRespPayTimeout(){
        return DynamicPropertyUtil.getIntPropertyValue(Constants.DynamicConfigKey.RESP_PAY_TIMEOUT, 600);
    }

    public Integer getFirstRespGetExceptionTimeout(){
        return DynamicPropertyUtil.getIntPropertyValue(Constants.DynamicConfigKey.FIRST_RESP_GET_EXCEPTION_TIMEOUT, 1800);
    }

    public Integer getConsecutiveRespGetExceptionTimeout(){
        return DynamicPropertyUtil.getIntPropertyValue(Constants.DynamicConfigKey.CONSECUTIVE_RESP_GET_EXCEPTION_TIMEOUT, 120);
    }

    public Integer getFirstRespQueryExceptionTimeout(){
        return DynamicPropertyUtil.getIntPropertyValue(Constants.DynamicConfigKey.FIRST_RESP_QUERY_EXCEPTION_TIMEOUT, 20);
    }

    public Integer getConsecutiveRespQueryExceptionTimeout(){
        return DynamicPropertyUtil.getIntPropertyValue(Constants.DynamicConfigKey.CONSECUTIVE_RESP_QUERY_EXCEPTION_TIMEOUT, 10);
    }

    public Integer getReqGetExceptionLocalRetryTimeout(){
        return DynamicPropertyUtil.getIntPropertyValue(Constants.DynamicConfigKey.REQ_GET_EXCEPTION_LOCAL_RETRY_TIMEOUT, 120);
    }

    public Integer getReqQueryExceptionLocalRetryTimeout(){
        return DynamicPropertyUtil.getIntPropertyValue(Constants.DynamicConfigKey.REQ_QUERY_EXCEPTION_LOCAL_RETRY_TIMEOUT, 10);
    }
    
    public Integer getNpciAutoRetryTimeout(){
        return DynamicPropertyUtil.getIntPropertyValue(Constants.DynamicConfigKey.NPCI_AUTO_RETRY_TIMEOUT, 600);
    }
}

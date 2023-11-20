package com.paytm.acquirer.netc.config.properties;

import com.paytm.acquirer.netc.util.Constants;
import com.paytm.transport.util.DynamicPropertyUtil;
import lombok.Data;
import org.springframework.stereotype.Component;

@Data
@Component
public class RetryProperties {
    public Integer getReqPayMaxRetry(){
        return DynamicPropertyUtil.getIntPropertyValue(Constants.DynamicConfigKey.REQ_PAY_MAX_RETRY, 3);
    }

    public Integer getReqGetExceptionMaxRetry(){
        return DynamicPropertyUtil.getIntPropertyValue(Constants.DynamicConfigKey.REQ_GET_EXCEPTION_MAX_RETRY, 1);
    }

    public Integer getReqQueryExceptionMaxRetry(){
        return DynamicPropertyUtil.getIntPropertyValue(Constants.DynamicConfigKey.REQ_QUERY_EXCEPTION_MAX_RETRY, 1);
    }

    public Integer getTimeSyncMaxRetry(){
        return DynamicPropertyUtil.getIntPropertyValue(Constants.DynamicConfigKey.TIME_SYNC_MAX_RETRY, 3);
    }

    public Integer getCheckTxnStatusMaxRetry(){
        return DynamicPropertyUtil.getIntPropertyValue(Constants.DynamicConfigKey.CHECK_TXN_STATUS_MAX_RETRY, 3);
    }

    public Integer getCheckTxnStatusReqPayMaxRetry(){
        return DynamicPropertyUtil.getIntPropertyValue(Constants.DynamicConfigKey.CHECK_TXN_STATUS_REQ_PAY_MAX_RETRY, 3);
    }

    public Integer getReqPayLocalMaxRetry(){
        return DynamicPropertyUtil.getIntPropertyValue(Constants.DynamicConfigKey.REQ_PAY_LOCAL_MAX_RETRY, 5);
    }
}

package com.paytm.acquirer.netc.exception;

import com.paytm.acquirer.netc.enums.ErrorMessage;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.http.HttpStatus;

import java.util.Objects;

@Data
@EqualsAndHashCode(callSuper = true)
public class NetcEngineException extends RuntimeException {

    private Integer responseCode;

    public NetcEngineException(String message, Throwable cause) {
        super(message, cause);
    }

    public NetcEngineException(String message) {
        super(message);
    }

    public NetcEngineException(final HttpStatus httpStatus, final String message) {
        this(httpStatus, message, null);
    }

    public NetcEngineException(
      final HttpStatus httpStatus, final String message, final Throwable cause) {
        super(message, cause, true, false);
        this.responseCode = (Objects.isNull(httpStatus) ? HttpStatus.INTERNAL_SERVER_ERROR : httpStatus).value();
    }

    public NetcEngineException(ErrorMessage errorMessage) {
        this(errorMessage.getHttpStatus(), errorMessage.getMessage());
    }

    public NetcEngineException(final ErrorMessage errorMessage, final Object... args) {
        this(errorMessage.getHttpStatus(), String.format(errorMessage.getMessage(), args));
    }

    public NetcEngineException(final ErrorMessage errorMessage, final Throwable cause) {
        this(errorMessage.getHttpStatus(), errorMessage.getMessage(), cause);
    }

}

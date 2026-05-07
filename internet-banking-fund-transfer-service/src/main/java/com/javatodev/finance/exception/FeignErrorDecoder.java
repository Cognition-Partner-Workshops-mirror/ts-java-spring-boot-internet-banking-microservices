package com.javatodev.finance.exception;

import feign.Response;
import feign.codec.ErrorDecoder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;

@Slf4j
public class FeignErrorDecoder implements ErrorDecoder {

    private final ErrorDecoder defaultDecoder = new Default();

    @Override
    public Exception decode(String methodKey, Response response) {
        HttpStatus status = HttpStatus.resolve(response.status());
        log.error("Feign error: method={}, status={}", methodKey, response.status());

        if (status == null) {
            return defaultDecoder.decode(methodKey, response);
        }

        return switch (status.series()) {
            case CLIENT_ERROR -> new ServiceException(
                "Downstream service rejected the request", status);
            case SERVER_ERROR -> new ServiceException(
                "Downstream service encountered an error", status);
            default -> defaultDecoder.decode(methodKey, response);
        };
    }
}

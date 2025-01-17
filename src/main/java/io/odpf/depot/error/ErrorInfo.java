package io.odpf.depot.error;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;

@AllArgsConstructor
@Data
public class ErrorInfo {

    @EqualsAndHashCode.Exclude private Exception exception;
    private ErrorType errorType;

    public String toString() {
        return errorType.name();
    }
}

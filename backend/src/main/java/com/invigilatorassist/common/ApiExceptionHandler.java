package com.invigilatorassist.common;

import com.invigilatorassist.allocation.engine.AllocationException;
import com.invigilatorassist.imports.ImportFailedException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/** Every error leaves the API as an RFC 9457 problem+json body. */
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(ApiException.class)
    ProblemDetail handleApi(ApiException ex) {
        return ProblemDetail.forStatusAndDetail(ex.getStatus(), ex.getMessage());
    }

    @ExceptionHandler(AllocationException.class)
    ProblemDetail handleAllocation(AllocationException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        pd.setTitle("Allocation not possible");
        return pd;
    }

    @ExceptionHandler(ImportFailedException.class)
    ProblemDetail handleImport(ImportFailedException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY,
                ex.getErrors().size() + " row(s) have errors; nothing was imported");
        pd.setTitle("Import failed");
        pd.setProperty("errors", ex.getErrors());
        return pd;
    }
}

package com.invigilatorassist.imports;

import java.util.List;

/** Thrown when any CSV row is invalid; the whole import is rolled back. */
public class ImportFailedException extends RuntimeException {
    private final List<String> errors;

    public ImportFailedException(List<String> errors) {
        super(String.join("; ", errors));
        this.errors = List.copyOf(errors);
    }

    public List<String> getErrors() {
        return errors;
    }
}

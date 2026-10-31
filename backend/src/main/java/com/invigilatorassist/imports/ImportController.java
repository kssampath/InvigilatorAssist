package com.invigilatorassist.imports;

import com.invigilatorassist.common.ApiException;
import com.invigilatorassist.imports.CsvImportService.ImportResult;
import com.invigilatorassist.imports.CsvImportService.Kind;
import com.invigilatorassist.security.CurrentUser;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Locale;

/**
 * Upload order matters for references: departments → rooms → faculty → subjects → students → backlogs.
 * The response lists temporary passwords for newly created accounts exactly once; distribute them securely.
 */
@RestController
@RequestMapping("/api/admin/import")
public class ImportController {

    private final CsvImportService service;

    public ImportController(CsvImportService service) {
        this.service = service;
    }

    @PostMapping(path = "/{kind}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ImportResult upload(@AuthenticationPrincipal Jwt jwt, @PathVariable String kind,
                               @RequestParam("file") MultipartFile file) throws IOException {
        Kind k;
        try {
            k = Kind.valueOf(kind.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest("Unknown import type '" + kind + "'");
        }
        if (file.isEmpty()) throw ApiException.badRequest("The uploaded file is empty");
        return service.importCsv(CurrentUser.of(jwt).institutionId(), k, file.getInputStream());
    }
}

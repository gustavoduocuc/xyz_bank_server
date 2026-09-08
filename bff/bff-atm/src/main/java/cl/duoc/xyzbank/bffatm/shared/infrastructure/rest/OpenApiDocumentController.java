package cl.duoc.xyzbank.bffatm.shared.infrastructure.rest;

import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class OpenApiDocumentController {

    @GetMapping(value = "/v3/api-docs", produces = {"application/yaml", "application/json", "text/plain"})
    public ResponseEntity<Resource> getDocument() {
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/yaml"))
                .body(new ClassPathResource("openapi/openapi.yaml"));
    }
}

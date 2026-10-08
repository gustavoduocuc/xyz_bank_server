package cl.duoc.xyzbank.coreservice.postings.infrastructure.rest;

import cl.duoc.xyzbank.coreservice.postings.application.dto.PostingRequest;
import cl.duoc.xyzbank.coreservice.postings.application.dto.PostingResponse;
import cl.duoc.xyzbank.coreservice.postings.application.usecases.ApplyPostingsUseCase;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PostingController {

    private final ApplyPostingsUseCase applyPostingsUseCase;

    public PostingController(ApplyPostingsUseCase applyPostingsUseCase) {
        this.applyPostingsUseCase = applyPostingsUseCase;
    }

    @PostMapping("/internal/postings")
    public ResponseEntity<PostingResponse> post(@RequestBody PostingRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(applyPostingsUseCase.execute(request));
    }
}

package com.moapan.identity.api;

import com.moapan.identity.domain.TermsDocument;
import com.moapan.identity.infrastructure.TermsDocuments;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.web.bind.annotation.*;

@RestController @ConditionalOnWebApplication
public class TermsController {
    private final TermsDocuments documents;
    public TermsController(TermsDocuments documents){this.documents=documents;}
    @GetMapping("/api/v1/auth/terms") TermsDocument current(){return documents.current();}
}

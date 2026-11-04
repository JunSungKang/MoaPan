package com.moapan.identity.domain;

import java.util.List;

/** Readable policy content only: no markup attributes, links or executable content. */
public record TermsDocument(String version,List<Part> nodes) {
    public TermsDocument { nodes=List.copyOf(nodes); }
    public record Part(String kind,String text,List<Part> children) {
        public Part { children=List.copyOf(children); }
    }
}

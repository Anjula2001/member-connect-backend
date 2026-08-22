package com.memberconnect.backend.enums;

/**
 * The four Member Profile Change Request types in scope for Requirement 02.
 *
 * Single source of truth for three things that were previously repeated as string
 * literals across the backend and the frontend, and had already drifted apart:
 *
 *  - requestPrefix        the code in the generated Request ID (PCR-2026-001)
 *  - documentType         the RequiredDocument.applicationType this request reads
 *  - label                the wording of the 'Type' dropdown on the unified list
 *
 * Member Transfer is deliberately absent: it is covered by Requirement 02 section 6
 * but is out of scope for this work, and lives in MemberTransferRequest with its own
 * status enum.
 */
public enum ProfileChangeType {

    BASIC_PROFILE("PCR", "BASIC_PROFILE_CHANGE", "Basic Profile Changes"),
    NAME("NCR", "NAME_CHANGE", "Name Changes"),
    NOMINEE("NMR", "NOMINEE_CHANGE", "Nominee Changes"),
    REMITTANCE("RCR", "REMITTANCE_CHANGE", "Remittance Amount Changes");

    private final String requestPrefix;
    private final String documentType;
    private final String label;

    ProfileChangeType(String requestPrefix, String documentType, String label) {
        this.requestPrefix = requestPrefix;
        this.documentType = documentType;
        this.label = label;
    }

    public String getRequestPrefix() {
        return requestPrefix;
    }

    public String getDocumentType() {
        return documentType;
    }

    public String getLabel() {
        return label;
    }
}

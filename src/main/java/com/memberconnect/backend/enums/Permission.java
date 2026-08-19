package com.memberconnect.backend.enums;

/**
 * Fine-grained rights for the Grade 5 Scholarship module (MMS01-MMS20).
 *
 * These exist because "role" alone is too coarse for what the SRS actually asks
 * for. Section 2.3.4 keeps talking about "the user needs Inactive rights" and
 * MMS09/MMS16 about "the authorized user who has the delete privileges" — those
 * are rights held in addition to a role, not roles in their own right. Modelling
 * them as named permissions lets a controller say exactly which right it needs
 * instead of re-listing roles at every call site.
 *
 * Roles map to permissions in {@link com.memberconnect.backend.config.RolePermissions}.
 * Nothing outside the Grade 5 module is governed here — Member Registration keeps
 * its own role checks, and University Scholarships / Death Donation are untouched.
 */
public enum Permission {

    // ---- Requests (MMS01-MMS05) : District Office owns creation ----
    G5_REQUEST_VIEW,
    G5_REQUEST_CREATE,
    G5_REQUEST_EDIT,
    G5_REQUEST_SUBMIT,
    G5_REQUEST_INCOMPLETE,

    /** Set a request to Inactive — SRS 2.3.4 "the user needs Inactive rights". */
    G5_REQUEST_SET_INACTIVE,

    /**
     * Move a request back to New once the Board has already Rejected it.
     * Split out from ordinary edit rights on purpose: reopening a rejection
     * overturns a Board decision, so it does not belong to the District Office
     * clerk who raised the request.
     */
    G5_REQUEST_REOPEN,

    // ---- Approval lists (MMS06-MMS19) : Head Office owns approval ----
    G5_LIST_VIEW,
    G5_LIST_CREATE,
    G5_LIST_PRINT,

    /** Approve / reject the requests on a list after the Board Meeting. */
    G5_LIST_PROCESS,

    /** MMS09 / MMS16 — "delete privileges", deliberately narrower than list access. */
    G5_LIST_DELETE,

    // ---- Masters ----
    /** Grade 5 Exam Master: exam dates and per-district cut-off marks. */
    G5_EXAM_MASTER_VIEW,
    G5_EXAM_MASTER_MANAGE,

    /**
     * MMS20 — hand approved scholarships to the Finance Module for disbursement.
     * Defined now so the matrix is complete; there is no endpoint behind it yet.
     */
    G5_FINANCE_DISBURSE
}

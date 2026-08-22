package com.memberconnect.backend.enums;

/**
 * Fine-grained rights for the Grade 5 Scholarship module (MMS01-MMS20), the
 * University Scholarship module (MMS21-MMS48) and the Member Retirement module
 * (MMT12-MMT17).
 *
 * These exist because "role" alone is too coarse for what the SRS actually asks
 * for. Section 2.3.4 keeps talking about "the user needs Inactive rights" and
 * MMS09/MMS16 about "the authorized user who has the delete privileges" — those
 * are rights held in addition to a role, not roles in their own right. Modelling
 * them as named permissions lets a controller say exactly which right it needs
 * instead of re-listing roles at every call site. The Retirement SRS repeats the
 * same pattern ("the Authorized User", "the user needs Inactive rights", "if the
 * logged in user has the rights to change the status", "if the user has edit
 * rights"), so it reuses this enum rather than growing a second scheme.
 *
 * Roles map to permissions in {@link com.memberconnect.backend.config.RolePermissions}.
 * Member Registration keeps its own role checks, and Termination / Death Donation
 * are untouched.
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
     * Defined so the matrix is complete; no endpoint is guarded by it, because the
     * handoff runs automatically when the Board's approval list is processed rather
     * than being triggered by a user. See
     * Grade5ScholarshipApprovalListService#callFinanceModuleApi.
     */
    G5_FINANCE_DISBURSE,

    // ---- Retirement requests (MMT12-MMT16) : District Office raises, Head Office approves ----
    RET_REQUEST_VIEW,
    RET_REQUEST_CREATE,

    /** MMT15 / 3.2.2 — "if the logged in user has edit rights". */
    RET_REQUEST_EDIT,

    RET_REQUEST_SUBMIT,
    RET_REQUEST_INCOMPLETE,

    /** Set a request to Inactive — SRS 3.2.4 "the user needs Inactive rights". */
    RET_REQUEST_SET_INACTIVE,

    /**
     * Move a request back to New from Submitted for Approval, Rejected or Inactive.
     * Split out from ordinary edit rights on purpose: SRS 3.2.1 qualifies the
     * Submitted -> New move with "if the logged in user has the rights to change the
     * status", and the Rejected -> New move overturns an approval decision. The
     * everyday Incomplete -> New fix-and-carry-on path stays on RET_REQUEST_EDIT.
     */
    RET_REQUEST_RETURN_TO_NEW,

    /** MMT16 — the "Authorized User" who may Approve or Reject a submitted request. */
    RET_REQUEST_APPROVE,

    // ================= University Scholarships (MMS21-MMS48) =================
    //
    // Kept in a separate US_* namespace rather than reused from G5_*, because this
    // module has two things Grade 5 does not: a Committee approval level that sits
    // before the Board, and a second request type (Fund Requests) with its own
    // lifecycle. Sharing the Grade 5 rights would silently merge those levels.

    // ---- Requests (MMS21-MMS25) : District Office territory ----
    US_REQUEST_VIEW,
    US_REQUEST_CREATE,
    US_REQUEST_EDIT,
    US_REQUEST_SUBMIT,
    US_REQUEST_INCOMPLETE,

    /** SRS 3.3.4 — "the user needs Inactive rights". */
    US_REQUEST_SET_INACTIVE,

    /** Move a Rejected request back to New. Reverses a committee or board decision. */
    US_REQUEST_REOPEN,

    /**
     * MMS26 — University Scholarship Committee approve/reject.
     *
     * Deliberately held by a role that does NOT hold US_LIST_PROCESS. The SRS places
     * the Committee at Head Office, but Head Office also runs the Board step; if one
     * role held both, a single person could walk a request from submission to final
     * approval and the Committee gate would be decorative.
     */
    US_COMMITTEE_APPROVE,

    // ---- Board approval lists (MMS27-MMS40) : Head Office territory ----
    US_LIST_VIEW,
    US_LIST_CREATE,
    US_LIST_PRINT,
    US_LIST_PROCESS,
    US_LIST_DELETE,

    /**
     * MMS41 — amend an already-Approved scholarship (academic start date, special
     * degree flag, and the disbursement bank account).
     *
     * Its own right because the SRS calls it "a special authorization" and because it
     * changes the payee on a live award — that must not ride on ordinary edit rights.
     */
    US_APPROVED_EDIT,

    // ---- Fund requests / disbursement (MMS42-MMS47) ----
    US_FUND_VIEW,
    US_FUND_CREATE,
    US_FUND_EDIT,
    US_FUND_SUBMIT,
    US_FUND_INCOMPLETE,

    /**
     * MMS47 — approve/reject a disbursement.
     *
     * Separated from US_FUND_CREATE and US_APPROVED_EDIT so that changing the bank
     * account and releasing money to it are not the same person's decision.
     */
    US_FUND_APPROVE,

    /**
     * Deactivate a fund request from View Mode — the fund-request counterpart of
     * US_REQUEST_SET_INACTIVE. Kept in the US_FUND_* namespace rather than reusing the
     * request-level right, because a fund request has its own lifecycle and the two
     * must be movable between roles independently.
     */
    US_FUND_SET_INACTIVE,

    /**
     * Return a fund request to New from View Mode — including out of Rejected or
     * Inactive, which reverses a disbursement decision. Held apart from US_FUND_EDIT
     * so the office that prepares a fund request cannot undo its rejection.
     */
    US_FUND_REOPEN,

    // ---- Masters (universities, programs, exam years) ----
    US_MASTER_VIEW,
    US_MASTER_MANAGE,

    /** MMS48 — hand approved fund requests to Finance. No endpoint behind it yet. */
    US_FINANCE_DISBURSE
}

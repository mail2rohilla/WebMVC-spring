package com.paytm.acquirer.netc.enums;

public enum TransactionType {
    FETCH,
    DEBIT,
    CREDIT,
    NON_FIN,
    FETCHEXCEPTION,
    Query, // casing is correct as specified in docs // NOSONAR
    ChkTxn, // NOSONAR
    ManageException, // NOSONAR
    ListParticipant   // NOSONAR
}

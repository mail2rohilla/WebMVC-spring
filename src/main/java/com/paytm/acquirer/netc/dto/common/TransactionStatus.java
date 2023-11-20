package com.paytm.acquirer.netc.dto.common;

import lombok.Data;
import java.util.Set;

@Data
public class TransactionStatus {

  private Set<String> refIds;
}

package com.paytm.acquirer.netc.db.repositories.slave;

import com.paytm.acquirer.netc.db.entities.AsyncTransaction;
import com.paytm.acquirer.netc.enums.NetcEndpoint;
import com.paytm.acquirer.netc.enums.Status;
import com.paytm.transport.metrics.Monitor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Set;

@Repository
public interface AsyncTransactionSlaveRepository extends JpaRepository<AsyncTransaction, Long> {

  @Monitor(name = "findByApiAndRefIdInOrderByIdAscSlave", metricGroup = Monitor.ServiceGroup.DB)
  List<AsyncTransaction> findByApiAndRefIdInOrderByIdAsc(NetcEndpoint api, Set<String> txnReferenceId);

  @Monitor(name = "findByApiAndStatusAndRefIdInOrderByIdAscSlave", metricGroup = Monitor.ServiceGroup.DB)
  List<AsyncTransaction> findByApiAndStatusInAndRefIdInOrderByIdAsc(NetcEndpoint api, List<Status> status, Set<String> txnReferenceId);

}

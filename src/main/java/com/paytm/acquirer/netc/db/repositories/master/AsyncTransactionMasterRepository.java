package com.paytm.acquirer.netc.db.repositories.master;

import com.paytm.acquirer.netc.db.entities.AsyncTransaction;
import com.paytm.acquirer.netc.enums.NetcEndpoint;
import com.paytm.acquirer.netc.enums.Status;
import com.paytm.transport.metrics.Monitor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Repository
public interface AsyncTransactionMasterRepository extends JpaRepository<AsyncTransaction, Long> {

    @Monitor(name = "findAsyncTransactionByMsgId&Api", metricGroup = Monitor.ServiceGroup.DB)
    Optional<AsyncTransaction> findByMsgIdAndApi(String msgId, NetcEndpoint endpoint);

    @Monitor(name = "findAsyncTransactionFirstByMsgId&ApiIn", metricGroup = Monitor.ServiceGroup.DB)
    Optional<AsyncTransaction> findFirstByMsgIdAndApiIn(String msgId, Set<NetcEndpoint> apis);

    @Monitor(name = "findAsyncTransactionByMsgId&Api&MsgNum", metricGroup = Monitor.ServiceGroup.DB)
    Optional<AsyncTransaction> findByMsgIdAndApiAndMsgNum(
      String msgId, NetcEndpoint api, Integer msgNum);

    @Monitor(name = "findAsyTxnByMsgId&Api&OrderByCreatedAtDesc", metricGroup = Monitor.ServiceGroup.DB)
    Optional<AsyncTransaction> findFirstByMsgIdAndApiOrderByCreatedAtDesc(
      String msgId, NetcEndpoint api);

    @Monitor(name = "findFirstByApi&RefIdOrderById", metricGroup = Monitor.ServiceGroup.DB)
    Optional<AsyncTransaction> findFirstByApiAndRefIdOrderByIdDesc(NetcEndpoint api, String txnReferenceId);

    @Monitor(name = "findByApi&RefIdInOrderByIdAsc", metricGroup = Monitor.ServiceGroup.DB)
    List<AsyncTransaction> findByApiAndRefIdInOrderByIdAsc(NetcEndpoint api, Set<String> txnReferenceId);

    @Monitor(name = "findByApi&Status&RefIdInOrderByIdAsc", metricGroup = Monitor.ServiceGroup.DB)
    List<AsyncTransaction> findByApiAndStatusInAndRefIdInOrderByIdAsc(NetcEndpoint api, List<Status> status, Set<String> txnReferenceId);

    @Monitor(name = "findByApi&RefId&CreatedAtLessThanEqualByDesc", metricGroup = Monitor.ServiceGroup.DB)
    Optional<AsyncTransaction> findFirstByApiAndRefIdAndCreatedAtLessThanEqualOrderByIdDesc(
        NetcEndpoint api, String refId, Timestamp createdAt);

    @Monitor(name = "findByApiOrderByIdDesc", metricGroup = Monitor.ServiceGroup.DB)
    List<AsyncTransaction> findByApiOrderByIdDesc(NetcEndpoint netcEndpoint, Pageable pageable);

}

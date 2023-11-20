package com.paytm.acquirer.netc.db.repositories.slave;

import com.paytm.acquirer.netc.db.entities.ErrorCodeMapping;
import com.paytm.transport.metrics.Monitor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ErrorCodeMappingSlaveRepository extends JpaRepository<ErrorCodeMapping, Long> {
  @Monitor(name = "findErrorCodeMappingByErrorCodeSlave", metricGroup = Monitor.ServiceGroup.DB)
  Optional<ErrorCodeMapping> findByErrorCode(String errorCode);

  @Monitor(name = "findErrorCodeMappingByHandlerNotNullSlave", metricGroup = Monitor.ServiceGroup.DB)
  List<ErrorCodeMapping> findByHandlerNotNull();
}

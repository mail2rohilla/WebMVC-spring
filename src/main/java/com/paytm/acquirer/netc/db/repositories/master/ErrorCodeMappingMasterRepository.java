package com.paytm.acquirer.netc.db.repositories.master;

import com.paytm.acquirer.netc.db.entities.ErrorCodeMapping;
import com.paytm.acquirer.netc.enums.HandlerType;
import com.paytm.transport.metrics.Monitor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ErrorCodeMappingMasterRepository extends JpaRepository<ErrorCodeMapping, Long> {
  @Monitor(name = "findErrorCodeMappingByErrorCode", metricGroup = Monitor.ServiceGroup.DB)
  Optional<ErrorCodeMapping> findByErrorCode(String errorCode);

  @Monitor(name = "findErrorCodeMappingByHandlerNotNull", metricGroup = Monitor.ServiceGroup.DB)
  List<ErrorCodeMapping> findByHandlerNotNull();

  @Monitor(name = "findByHandler", metricGroup = Monitor.ServiceGroup.DB)
  List<ErrorCodeMapping> findByHandler(HandlerType handlerType);
}

package com.paytm.acquirer.netc.db.repositories.slave;

import com.paytm.acquirer.netc.db.entities.IinInfo;
import com.paytm.transport.metrics.Monitor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface IinInfoSlaveRepository extends JpaRepository<IinInfo, Long> {
  
  @Monitor(name = "findAllIinData", metricGroup = Monitor.ServiceGroup.DB)
  List<IinInfo> findAll();
  
  @Monitor(name = "findByIsActiveOrderByIdDesc", metricGroup = Monitor.ServiceGroup.DB)
  List<IinInfo> findByIsActiveOrderByIdDesc(boolean isActive);
  
  @Monitor(name = "findByIsActiveAndIssuerIinInOrderByIdDesc", metricGroup = Monitor.ServiceGroup.DB)
  List<IinInfo> findByIsActiveAndIssuerIinInOrderByIdDesc(boolean isActive ,List<String> issuerIins);
}

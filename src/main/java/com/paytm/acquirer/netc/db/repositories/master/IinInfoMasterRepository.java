package com.paytm.acquirer.netc.db.repositories.master;

import com.paytm.acquirer.netc.db.entities.IinInfo;
import com.paytm.transport.metrics.Monitor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface IinInfoMasterRepository extends JpaRepository<IinInfo, Long> {
  
  @Monitor(name= "saveAllIinList", metricGroup = Monitor.ServiceGroup.DB)
  @Override
  <S extends IinInfo> List<S> saveAll(Iterable<S> entities);
  
}

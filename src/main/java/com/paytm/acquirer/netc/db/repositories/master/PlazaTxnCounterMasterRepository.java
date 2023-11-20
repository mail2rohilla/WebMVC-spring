package com.paytm.acquirer.netc.db.repositories.master;


import com.paytm.acquirer.netc.db.entities.PlazaTxnCounter;
import com.paytm.transport.metrics.Monitor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import javax.transaction.Transactional;
import java.util.Optional;

@Repository
public interface PlazaTxnCounterMasterRepository extends JpaRepository<PlazaTxnCounter, Long> {
    Optional<PlazaTxnCounter> findByPlazaId(String plazaId);

    @Modifying
    @Transactional
    @Monitor(name = "updatePlazaTxnCounterToZero", metricGroup = Monitor.ServiceGroup.DB)
    @Query(value = "update netc_plaza_txn_counter set counter = 0", nativeQuery = true)
    void updateCounterToZero();
}

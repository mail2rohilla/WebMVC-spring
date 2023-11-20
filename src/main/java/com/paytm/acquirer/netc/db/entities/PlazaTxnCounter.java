package com.paytm.acquirer.netc.db.entities;

import lombok.Data;

import javax.persistence.*;

@Data
@Entity
@Table(name = "netc_plaza_txn_counter")
public class PlazaTxnCounter {
    @Id
    @Column(name = "id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "plaza_id")
    private String plazaId;

    //max length 4 digits
    @Column(name = "counter")
    private Integer counter;
}

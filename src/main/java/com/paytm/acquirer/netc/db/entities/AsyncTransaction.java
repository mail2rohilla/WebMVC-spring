package com.paytm.acquirer.netc.db.entities;

import com.paytm.acquirer.netc.db.converter.NetcEndpointConverter;
import com.paytm.acquirer.netc.db.converter.StatusTypeConverter;
import com.paytm.acquirer.netc.enums.NetcEndpoint;
import com.paytm.acquirer.netc.enums.Status;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import javax.persistence.*;
import java.sql.Timestamp;

@Data
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "netc_async_transactions")
public class AsyncTransaction {
    @Id
    @Column(name = "id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "msg_id", length = 35)
    private String msgId;

    @Column(name = "txn_id", length = 22)
    private String txnId;

    @Column(name = "ref_id", length = 35)
    private String refId;

    @Column(name = "status")
    @Convert(converter = StatusTypeConverter.class)
    private Status status;

    @Convert(converter = NetcEndpointConverter.class)
    @Column(name = "api", nullable = false,updatable = false)
    private NetcEndpoint api;

    @Column(name = "status_code", length = 3, nullable = false)
    private String statusCode;

    @Column(name = "msg_num")
    private Integer msgNum;

    @Column(name = "total_msg")
    private Integer totalMsg;

    @Column(name = "meta_data", columnDefinition = "jsonb")
    private String metaData;

    @CreationTimestamp
    @Column(name = "created_at")
    private Timestamp createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Timestamp updatedAt;

    @Column(name="retry_count")
    private Integer retryCount;

}

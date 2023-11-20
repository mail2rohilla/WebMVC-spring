package com.paytm.acquirer.netc.db.entities;

import com.paytm.acquirer.netc.db.converter.HandlerTypeConverter;
import com.paytm.acquirer.netc.enums.HandlerType;
import lombok.Data;

import javax.persistence.*;

@Data
@Entity
@Table(name = "netc_error_code_mapping")
public class ErrorCodeMapping {
    @Id
    @Column(name = "id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "error_code", length = 3)
    private String errorCode;

    @Column(name = "mapping", length = 1024)
    private String mapping;

    @Column(name = "handler")
    @Convert(converter = HandlerTypeConverter.class)
    private HandlerType handler;
}

package com.paytm.acquirer.netc.db.entities;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;
import java.sql.Timestamp;

@Data
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "iin_info")
public class IinInfo {
  
  @Id
  @Column(name = "id")
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;
  
  @Column(name= "short_code")
  private String shortCode;
  
  @Column(name = "acquirer_iin")
  private String acquirerIin;
  
  @Column(name = "issuer_iin")
  private String issuerIin;
  
  @Column(name = "role")
  private String role;
  
  @Column(name = "is_active")
  private boolean isActive;
  
  @Column(name = "name")
  private String bankName;
  
  @CreationTimestamp
  @Column(name = "created_at")
  private Timestamp createdAt;
  
  @UpdateTimestamp
  @Column(name = "updated_at")
  private Timestamp updatedAt;
  
}

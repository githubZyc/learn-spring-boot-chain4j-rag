package org.yc.learn.rag.qc.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.yc.learn.rag.qc.rule.QcSeverity;

import java.time.Instant;
import java.time.LocalDate;

/**
 * 质控异常。同一规则对同一病例只保留一条记录，重复扫描做更新而不是新增。
 *
 * @author zhengyanchuang
 */
@Entity
@Table(name = "qc_findings",
        uniqueConstraints = @UniqueConstraint(columnNames = {"rule_code", "record_id"}))
public class QcFindingEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "rule_code", nullable = false, length = 64)
    private String ruleCode;

    @Column(name = "rule_name", nullable = false, length = 100)
    private String ruleName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private QcSeverity severity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private QcFindingStatus status;

    @Column(name = "record_id", nullable = false)
    private long recordId;

    @Column(name = "record_no", length = 64)
    private String recordNo;

    @Column(name = "depart_id", length = 64)
    private String departId;

    @Column(name = "depart_name", length = 128)
    private String departName;

    @Column(name = "doctor_id", length = 64)
    private String doctorId;

    @Column(name = "doctor_name", length = 64)
    private String doctorName;

    @Column(name = "surgery_date")
    private LocalDate surgeryDate;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(length = 1000)
    private String detail;

    @Lob
    @Column(name = "evidence_json")
    private String evidenceJson;

    @Column(length = 64)
    private String owner;

    @Column(name = "first_found_at", nullable = false)
    private Instant firstFoundAt;

    @Column(name = "last_found_at", nullable = false)
    private Instant lastFoundAt;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Column(name = "resolved_by", length = 64)
    private String resolvedBy;

    @Column(name = "resolve_note", length = 500)
    private String resolveNote;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public String getRuleCode() {
        return ruleCode;
    }

    public void setRuleCode(String ruleCode) {
        this.ruleCode = ruleCode;
    }

    public String getRuleName() {
        return ruleName;
    }

    public void setRuleName(String ruleName) {
        this.ruleName = ruleName;
    }

    public QcSeverity getSeverity() {
        return severity;
    }

    public void setSeverity(QcSeverity severity) {
        this.severity = severity;
    }

    public QcFindingStatus getStatus() {
        return status;
    }

    public void setStatus(QcFindingStatus status) {
        this.status = status;
    }

    public long getRecordId() {
        return recordId;
    }

    public void setRecordId(long recordId) {
        this.recordId = recordId;
    }

    public String getRecordNo() {
        return recordNo;
    }

    public void setRecordNo(String recordNo) {
        this.recordNo = recordNo;
    }

    public String getDepartId() {
        return departId;
    }

    public void setDepartId(String departId) {
        this.departId = departId;
    }

    public String getDepartName() {
        return departName;
    }

    public void setDepartName(String departName) {
        this.departName = departName;
    }

    public String getDoctorId() {
        return doctorId;
    }

    public void setDoctorId(String doctorId) {
        this.doctorId = doctorId;
    }

    public String getDoctorName() {
        return doctorName;
    }

    public void setDoctorName(String doctorName) {
        this.doctorName = doctorName;
    }

    public LocalDate getSurgeryDate() {
        return surgeryDate;
    }

    public void setSurgeryDate(LocalDate surgeryDate) {
        this.surgeryDate = surgeryDate;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDetail() {
        return detail;
    }

    public void setDetail(String detail) {
        this.detail = detail;
    }

    public String getEvidenceJson() {
        return evidenceJson;
    }

    public void setEvidenceJson(String evidenceJson) {
        this.evidenceJson = evidenceJson;
    }

    public String getOwner() {
        return owner;
    }

    public void setOwner(String owner) {
        this.owner = owner;
    }

    public Instant getFirstFoundAt() {
        return firstFoundAt;
    }

    public void setFirstFoundAt(Instant firstFoundAt) {
        this.firstFoundAt = firstFoundAt;
    }

    public Instant getLastFoundAt() {
        return lastFoundAt;
    }

    public void setLastFoundAt(Instant lastFoundAt) {
        this.lastFoundAt = lastFoundAt;
    }

    public Instant getResolvedAt() {
        return resolvedAt;
    }

    public void setResolvedAt(Instant resolvedAt) {
        this.resolvedAt = resolvedAt;
    }

    public String getResolvedBy() {
        return resolvedBy;
    }

    public void setResolvedBy(String resolvedBy) {
        this.resolvedBy = resolvedBy;
    }

    public String getResolveNote() {
        return resolveNote;
    }

    public void setResolveNote(String resolveNote) {
        this.resolveNote = resolveNote;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}

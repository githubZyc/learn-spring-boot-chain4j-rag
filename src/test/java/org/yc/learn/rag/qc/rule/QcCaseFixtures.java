package org.yc.learn.rag.qc.rule;

import org.yc.learn.rag.config.AppProperties;
import org.yc.learn.rag.qc.domain.QcAnesthesia;
import org.yc.learn.rag.qc.domain.QcCase;
import org.yc.learn.rag.qc.domain.QcExtraction;
import org.yc.learn.rag.qc.domain.QcPlanting;
import org.yc.learn.rag.qc.domain.QcSetup;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 质控规则测试夹具，默认构造一例“健康、已归档”的病例。
 *
 * @author zhengyanchuang
 */
public final class QcCaseFixtures {

    private QcCaseFixtures() {
    }

    public static CaseBuilder builder() {
        return new CaseBuilder();
    }

    public static QcSetup setup() {
        return new QcSetup("1号手术室", "1.0mm", "后枕部", "无", "", "",
                "1", "video-1");
    }

    public static QcAnesthesia anesthesia(Integer minutes) {
        return new QcAnesthesia(
                LocalDateTime.of(2026, 11, 1, 9, 0),
                LocalDateTime.of(2026, 11, 1, 9, 30),
                minutes,
                "video-2",
                "麻醉师",
                "");
    }

    public static QcExtraction extraction(int regularSingle, int regularMulti) {
        return new QcExtraction(
                LocalDateTime.of(2026, 11, 1, 9, 30),
                LocalDateTime.of(2026, 11, 1, 11, 0),
                false,
                null,
                LocalDateTime.of(2026, 11, 1, 9, 30),
                LocalDateTime.of(2026, 11, 1, 10, 30),
                regularSingle,
                regularMulti,
                null,
                null,
                0,
                0,
                "video-3",
                "护士A");
    }

    public static QcExtraction longHairExtraction() {
        return new QcExtraction(
                LocalDateTime.of(2026, 11, 1, 9, 30),
                LocalDateTime.of(2026, 11, 1, 11, 0),
                false,
                null,
                LocalDateTime.of(2026, 11, 1, 9, 30),
                LocalDateTime.of(2026, 11, 1, 10, 30),
                800,
                400,
                LocalDateTime.of(2026, 11, 1, 10, 30),
                LocalDateTime.of(2026, 11, 1, 11, 0),
                0,
                0,
                "video-3",
                "护士A");
    }

    public static QcPlanting planting(int regularSingle, int regularMulti) {
        return new QcPlanting(
                LocalDateTime.of(2026, 11, 1, 11, 0),
                LocalDateTime.of(2026, 11, 1, 13, 0),
                regularSingle,
                regularMulti,
                "1,2",
                0,
                0,
                "",
                "video-4",
                "护士B");
    }

    public static AppProperties properties(int archiveDays, int anesthesiaMaxMin, double countDiffRatio) {
        return new AppProperties(
                null,
                0,
                null,
                null,
                null,
                null,
                null,
                new AppProperties.Qc(true, 30, archiveDays, anesthesiaMaxMin,
                        countDiffRatio, null, "0 0 6 * * *"));
    }

    public static final class CaseBuilder {

        private long id = 1;
        private String recordNo = "QC001";
        private String departId = "D1";
        private String departName = "城东分院";
        private LocalDate surgeryDate = LocalDate.of(2026, 11, 1);
        private String stage = "FINISHED";
        private int status = 4;
        private String technique = "1";
        private String chargeOrderId = "CHARGE-1";
        private String dzblNo = "DZBL-1";
        private String caseId = "CASE-1";
        private LocalDateTime surgeryStartTime = LocalDateTime.of(2026, 11, 1, 9, 0);
        private LocalDateTime surgeryEndTime = LocalDateTime.of(2026, 11, 1, 13, 0);
        private LocalDateTime filedTime = LocalDateTime.of(2026, 11, 3, 10, 0);
        private QcSetup setup = QcCaseFixtures.setup();
        private QcAnesthesia anesthesia = QcCaseFixtures.anesthesia(30);
        private QcExtraction extraction = QcCaseFixtures.extraction(1000, 500);
        private QcPlanting planting = QcCaseFixtures.planting(900, 500);

        public CaseBuilder id(long id) {
            this.id = id;
            return this;
        }

        public CaseBuilder stage(String stage) {
            this.stage = stage;
            return this;
        }

        public CaseBuilder status(int status) {
            this.status = status;
            return this;
        }

        public CaseBuilder technique(String technique) {
            this.technique = technique;
            return this;
        }

        public CaseBuilder chargeOrderId(String chargeOrderId) {
            this.chargeOrderId = chargeOrderId;
            return this;
        }

        public CaseBuilder dzblNo(String dzblNo) {
            this.dzblNo = dzblNo;
            return this;
        }

        public CaseBuilder caseId(String caseId) {
            this.caseId = caseId;
            return this;
        }

        public CaseBuilder filedTime(LocalDateTime filedTime) {
            this.filedTime = filedTime;
            return this;
        }

        public CaseBuilder surgeryDate(LocalDate surgeryDate) {
            this.surgeryDate = surgeryDate;
            return this;
        }

        public CaseBuilder setup(QcSetup setup) {
            this.setup = setup;
            return this;
        }

        public CaseBuilder anesthesia(QcAnesthesia anesthesia) {
            this.anesthesia = anesthesia;
            return this;
        }

        public CaseBuilder extraction(QcExtraction extraction) {
            this.extraction = extraction;
            return this;
        }

        public CaseBuilder planting(QcPlanting planting) {
            this.planting = planting;
            return this;
        }

        public QcCase build() {
            return new QcCase(
                    id, recordNo, departId, departName, surgeryDate, stage, status,
                    technique, "DOC-1", "NURSE-1", "护士A",
                    "ACC-1", "ACC-CODE-1", "客户A",
                    caseId, dzblNo, chargeOrderId,
                    surgeryStartTime, surgeryEndTime, filedTime, "归档人",
                    setup, anesthesia, extraction, planting);
        }
    }
}

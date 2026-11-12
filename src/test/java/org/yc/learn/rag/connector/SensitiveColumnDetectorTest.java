package org.yc.learn.rag.connector;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 敏感字段默认规则的单元测试。
 *
 * @author zhengyanchuang
 */
class SensitiveColumnDetectorTest {

    @Test
    void detectsCommonPersonalFields() {
        assertThat(SensitiveColumnDetector.isSensitive("name", Set.of())).isTrue();
        assertThat(SensitiveColumnDetector.isSensitive("patient_name", Set.of())).isTrue();
        assertThat(SensitiveColumnDetector.isSensitive("id_card", Set.of())).isTrue();
        assertThat(SensitiveColumnDetector.isSensitive("mobile", Set.of())).isTrue();
        assertThat(SensitiveColumnDetector.isSensitive("home_address", Set.of())).isTrue();
        assertThat(SensitiveColumnDetector.isSensitive("diagnosis", Set.of())).isTrue();
    }

    @Test
    void detectsSuffixVariants() {
        assertThat(SensitiveColumnDetector.isSensitive("patient_idcard", Set.of())).isTrue();
        assertThat(SensitiveColumnDetector.isSensitive("contact_phone", Set.of())).isTrue();
        assertThat(SensitiveColumnDetector.isSensitive("user_email", Set.of())).isTrue();
    }

    @Test
    void doesNotFlagOrdinaryBusinessColumns() {
        assertThat(SensitiveColumnDetector.isSensitive("dept_name", Set.of())).isFalse();
        assertThat(SensitiveColumnDetector.isSensitive("company_name", Set.of())).isFalse();
        assertThat(SensitiveColumnDetector.isSensitive("amount", Set.of())).isFalse();
        assertThat(SensitiveColumnDetector.isSensitive("order_no", Set.of())).isFalse();
    }

    @Test
    void supportsBusinessConfiguredColumns() {
        assertThat(SensitiveColumnDetector.isSensitive("blnr", Set.of("blnr"))).isTrue();
        assertThat(SensitiveColumnDetector.isSensitive("BLNR", Set.of("blnr"))).isTrue();
    }

    @Test
    void ignoresNullOrBlankColumnName() {
        assertThat(SensitiveColumnDetector.isSensitive(null, Set.of())).isFalse();
        assertThat(SensitiveColumnDetector.isSensitive("  ", Set.of())).isFalse();
    }
}

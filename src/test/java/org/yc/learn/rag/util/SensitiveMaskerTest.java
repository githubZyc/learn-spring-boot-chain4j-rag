package org.yc.learn.rag.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 敏感字段脱敏规则的单元测试。
 *
 * @author zhengyanchuang
 */
class SensitiveMaskerTest {

    @Test
    void masksNames() {
        assertThat(SensitiveMasker.mask("c__xingming", "张三")).isEqualTo("张*");
        assertThat(SensitiveMasker.mask("patient_name", "李")).isEqualTo("*");
    }

    @Test
    void masksIdCards() {
        assertThat(SensitiveMasker.mask("id_card", "110101199001011234"))
                .isEqualTo("110********1234");
        assertThat(SensitiveMasker.mask("sfzh", "123")).isEqualTo("***");
    }

    @Test
    void masksPhones() {
        assertThat(SensitiveMasker.mask("mobilePhone", "13800138000")).isEqualTo("138****8000");
        assertThat(SensitiveMasker.mask("c__lianxidianhua", "123")).isEqualTo("***");
    }

    @Test
    void masksEmails() {
        assertThat(SensitiveMasker.mask("email", "abc@example.com")).isEqualTo("a***@example.com");
    }

    @Test
    void fallsBackToFullMask() {
        assertThat(SensitiveMasker.mask("diagnosis", "高血压")).isEqualTo("***");
    }

    @Test
    void keepsNullAsNull() {
        assertThat(SensitiveMasker.mask("c__xingming", null)).isNull();
    }
}

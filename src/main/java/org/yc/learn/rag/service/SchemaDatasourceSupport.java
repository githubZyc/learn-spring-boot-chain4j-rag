package org.yc.learn.rag.service;

import org.yc.learn.rag.config.AppProperties;
import org.yc.learn.rag.connector.ConnectorConfig;
import org.yc.learn.rag.connector.mysql.SchemaExtractOptions;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 数据库知识库数据源配置的解析与校验，供同步和查询共用。
 *
 * @author zhengyanchuang
 */
public final class SchemaDatasourceSupport {

    private SchemaDatasourceSupport() {
    }

    public static boolean configured(AppProperties.Schema schema) {
        return schema != null
                && schema.datasource() != null
                && !isBlank(schema.datasource().url())
                && !isBlank(schema.datasource().username());
    }

    public static String database(AppProperties.SchemaDataSource datasource) {
        if (!isBlank(datasource.database())) {
            return datasource.database().trim();
        }
        try {
            return SchemaExtractOptions.parseDatabase(datasource.url());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public static ConnectorConfig connectorConfig(AppProperties.SchemaDataSource datasource) {
        Map<String, String> props = new HashMap<>();
        putIfNotBlank(props, "url", datasource.url());
        putIfNotBlank(props, "username", datasource.username());
        putIfNotBlank(props, "password", datasource.password());
        putIfNotBlank(props, "database", datasource.database());
        putIfNotBlank(props, "defaultTimeColumn", datasource.defaultTimeColumn());
        putIfNotBlank(props, "tableWhitelist", datasource.tableWhitelist());
        putIfNotBlank(props, "extraSensitiveColumns", datasource.extraSensitiveColumns());

        // 别名文件优先：它保证 UTF-8 正确解码；文件不存在时再退回环境变量
        String aliases = readAliasesFile(datasource.tableAliasesFile());
        if (isBlank(aliases)) {
            aliases = datasource.tableAliases();
        }
        putIfNotBlank(props, "tableAliases", aliases);
        return new ConnectorConfig(props);
    }

    /**
     * 从 UTF-8 文件读取表别名，每行 {@code 表名=别名}，忽略空行与 # 注释。
     * <p>
     * 单独走文件而不是 .env，是因为 .env 按 properties 解析时中文会被当成 Latin-1。
     */
    private static String readAliasesFile(String path) {
        if (isBlank(path)) {
            return null;
        }
        Path file = Path.of(path);
        if (!Files.exists(file)) {
            return null;
        }
        try {
            List<String> entries = new ArrayList<>();
            for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                String trimmed = line.trim();
                if (!trimmed.isEmpty() && !trimmed.startsWith("#")) {
                    entries.add(trimmed);
                }
            }
            return entries.isEmpty() ? null : String.join(",", entries);
        } catch (IOException e) {
            throw new IllegalStateException("读取表别名文件失败：" + path, e);
        }
    }

    private static void putIfNotBlank(Map<String, String> target, String key, String value) {
        if (!isBlank(value)) {
            target.put(key, value);
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}

package com.cev.movie.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Hadoop 集群配置属性。
 *
 * <p>用于承接 application.yml 中的 Hadoop/HDFS/Spark 路径配置，后续 Job、
 * 数据导入任务和离线分析触发器可直接注入使用。</p>
 */
@Component
@ConfigurationProperties(prefix = "movie.hadoop")
public class HadoopProperties {

    /** HDFS NameNode 地址，例如 hdfs://192.168.56.101:9000。 */
    private String hdfsUri;

    /** 原始影视数据在 HDFS 中的目录。 */
    private String rawDataPath;

    /** 清洗后数据在 HDFS 中的目录。 */
    private String cleanDataPath;

    /** 离线分析结果在 HDFS 中的目录。 */
    private String analyticsResultPath;

    public String getHdfsUri() {
        return hdfsUri;
    }

    public void setHdfsUri(String hdfsUri) {
        this.hdfsUri = hdfsUri;
    }

    public String getRawDataPath() {
        return rawDataPath;
    }

    public void setRawDataPath(String rawDataPath) {
        this.rawDataPath = rawDataPath;
    }

    public String getCleanDataPath() {
        return cleanDataPath;
    }

    public void setCleanDataPath(String cleanDataPath) {
        this.cleanDataPath = cleanDataPath;
    }

    public String getAnalyticsResultPath() {
        return analyticsResultPath;
    }

    public void setAnalyticsResultPath(String analyticsResultPath) {
        this.analyticsResultPath = analyticsResultPath;
    }
}

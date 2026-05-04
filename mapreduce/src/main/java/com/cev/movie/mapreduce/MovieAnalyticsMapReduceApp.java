package com.cev.movie.mapreduce;

import com.cev.movie.mapreduce.job.GenreStatJob;
import com.cev.movie.mapreduce.job.HotMovieRecommendationJob;
import com.cev.movie.mapreduce.job.HybridRecommendationJob;
import com.cev.movie.mapreduce.job.ItemCollaborativeFilteringJob;
import com.cev.movie.mapreduce.job.MovieHotScoreJob;
import com.cev.movie.mapreduce.job.UserCollaborativeFilteringJob;
import com.cev.movie.mapreduce.job.UserGenrePreferenceJob;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.conf.Configured;
import org.apache.hadoop.util.Tool;
import org.apache.hadoop.util.ToolRunner;

/**
 * 影视离线分析 MapReduce 总入口。
 *
 * <p>该模块运行在云服务器 Hadoop 环境中，读取 HDFS 上由 MySQL 表导出的 TSV/CSV 数据，
 * 计算题材统计、热门电影、用户偏好与个性化推荐，并把结果直接写回 MySQL 分析结果表。</p>
 */
public class MovieAnalyticsMapReduceApp extends Configured implements Tool {

    @Override
    public int run(String[] args) throws Exception {
        JobArguments jobArguments = JobArguments.parse(args);
        Configuration configuration = getConf();
        jobArguments.applyTo(configuration);

        String jobName = jobArguments.get("job", "all");
        if ("genre".equalsIgnoreCase(jobName)) {
            return new GenreStatJob(configuration, jobArguments).run();
        }
        if ("hot".equalsIgnoreCase(jobName)) {
            return new MovieHotScoreJob(configuration, jobArguments).run();
        }
        if ("recommend".equalsIgnoreCase(jobName) || "genreRecommend".equalsIgnoreCase(jobName)) {
            try (java.sql.Connection connection = com.cev.movie.mapreduce.common.MysqlWriters.getConnection(com.cev.movie.mapreduce.common.MysqlConfig.from(configuration))) {
                com.cev.movie.mapreduce.common.MysqlWriters.prepareRecommendation(connection);
            }
            return new UserGenrePreferenceJob(configuration, jobArguments).run();
        }
        if ("hotRecommend".equalsIgnoreCase(jobName)) {
            return new HotMovieRecommendationJob(configuration, jobArguments).run();
        }
        if ("userCf".equalsIgnoreCase(jobName)) {
            return new UserCollaborativeFilteringJob(configuration, jobArguments).run();
        }
        if ("itemCf".equalsIgnoreCase(jobName)) {
            return new ItemCollaborativeFilteringJob(configuration, jobArguments).run();
        }
        if ("hybridRecommend".equalsIgnoreCase(jobName)) {
            return new HybridRecommendationJob(configuration, jobArguments).run();
        }
        if ("all".equalsIgnoreCase(jobName)) {
            int genreCode = new GenreStatJob(configuration, jobArguments).run();
            if (genreCode != 0) {
                return genreCode;
            }
            int hotCode = new MovieHotScoreJob(configuration, jobArguments).run();
            if (hotCode != 0) {
                return hotCode;
            }
            try (java.sql.Connection connection = com.cev.movie.mapreduce.common.MysqlWriters.getConnection(com.cev.movie.mapreduce.common.MysqlConfig.from(configuration))) {
                com.cev.movie.mapreduce.common.MysqlWriters.prepareRecommendation(connection);
            }
            int recommendCode = new UserGenrePreferenceJob(configuration, jobArguments).run();
            if (recommendCode != 0) {
                return recommendCode;
            }
            int hotRecommendCode = new HotMovieRecommendationJob(configuration, jobArguments).run();
            if (hotRecommendCode != 0) {
                return hotRecommendCode;
            }
            int userCfCode = new UserCollaborativeFilteringJob(configuration, jobArguments).run();
            if (userCfCode != 0) {
                return userCfCode;
            }
            int itemCfCode = new ItemCollaborativeFilteringJob(configuration, jobArguments).run();
            if (itemCfCode != 0) {
                return itemCfCode;
            }
            return new HybridRecommendationJob(configuration, jobArguments).run();
        }
        System.err.println("Unsupported job: " + jobName + ". Optional values: all, genre, hot, recommend, genreRecommend, hotRecommend, userCf, itemCf, hybridRecommend");
        return 2;
    }

    public static void main(String[] args) throws Exception {
        int exitCode = ToolRunner.run(new MovieAnalyticsMapReduceApp(), args);
        System.exit(exitCode);
    }
}

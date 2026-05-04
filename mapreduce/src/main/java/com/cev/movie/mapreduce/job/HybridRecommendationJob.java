package com.cev.movie.mapreduce.job;

import com.cev.movie.mapreduce.JobArguments;
import com.cev.movie.mapreduce.common.MysqlConfig;
import com.cev.movie.mapreduce.common.MysqlWriters;
import com.cev.movie.mapreduce.common.TsvUtils;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.Mapper;
import org.apache.hadoop.mapreduce.Reducer;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;
import org.apache.hadoop.mapreduce.lib.output.NullOutputFormat;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 多路召回融合推荐 Job。
 *
 * <p>输入 TSV 格式：
 * {@code user_id \t movie_id \t movie_title \t algorithm_type \t score \t reason}。
 * 该 Job 将题材偏好、热门推荐、协同过滤等多路召回按权重融合，生成最终 HYBRID 推荐结果。</p>
 */
public class HybridRecommendationJob {

    private final Configuration configuration;
    private final JobArguments arguments;

    public HybridRecommendationJob(Configuration configuration, JobArguments arguments) {
        this.configuration = configuration;
        this.arguments = arguments;
    }

    public int run() throws Exception {
        Job job = Job.getInstance(configuration, "movie-hybrid-recommendation");
        job.setJarByClass(HybridRecommendationJob.class);
        job.setMapperClass(HybridMapper.class);
        job.setReducerClass(HybridReducer.class);
        job.setMapOutputKeyClass(LongWritable.class);
        job.setMapOutputValueClass(Text.class);
        job.setOutputKeyClass(LongWritable.class);
        job.setOutputValueClass(Text.class);
        job.setNumReduceTasks(arguments.getInt("hybridRecommendReducers", 1));
        job.getConfiguration().setInt("movie.analytics.recommendTopN", arguments.getInt("recommendTopN", 20));
        job.setOutputFormatClass(NullOutputFormat.class);
        FileInputFormat.addInputPath(job, new Path(arguments.get("hybridRecommendInput", "/movie/mysql/recommendation_result_export")));
        return job.waitForCompletion(true) ? 0 : 1;
    }

    public static class HybridMapper extends Mapper<LongWritable, Text, LongWritable, Text> {
        private final LongWritable outKey = new LongWritable();

        @Override
        protected void map(LongWritable key, Text value, Context context) throws IOException, InterruptedException {
            String[] fields = TsvUtils.split(value.toString());
            long userId = TsvUtils.parseLong(TsvUtils.value(fields, 0), -1L);
            if (userId < 0) {
                return;
            }
            outKey.set(userId);
            context.write(outKey, value);
        }
    }

    public static class HybridReducer extends Reducer<LongWritable, Text, LongWritable, Text> {
        private Connection connection;
        private PreparedStatement statement;
        private int topN;

        @Override
        protected void setup(Context context) throws IOException {
            topN = context.getConfiguration().getInt("movie.analytics.recommendTopN", 20);
            try {
                connection = MysqlWriters.getConnection(MysqlConfig.from(context.getConfiguration()));
                statement = MysqlWriters.recommendationStatement(connection);
            } catch (SQLException e) {
                throw new IOException("初始化融合推荐 MySQL 写入失败", e);
            }
        }

        @Override
        protected void reduce(LongWritable userId, Iterable<Text> values, Context context) throws IOException {
            Map<Long, HybridCandidate> candidates = new HashMap<>();
            for (Text value : values) {
                String[] fields = TsvUtils.split(value.toString());
                long movieId = TsvUtils.parseLong(TsvUtils.value(fields, 1), -1L);
                if (movieId < 0) {
                    continue;
                }
                String title = TsvUtils.value(fields, 2);
                String algorithmType = TsvUtils.value(fields, 3);
                double score = TsvUtils.parseDouble(TsvUtils.value(fields, 4), 0D);
                String reason = TsvUtils.value(fields, 5);
                double weightedScore = score * weightOf(algorithmType);
                HybridCandidate candidate = candidates.computeIfAbsent(movieId, key -> new HybridCandidate(movieId, title));
                candidate.addScore(weightedScore, algorithmType, reason);
            }

            List<HybridCandidate> sorted = new ArrayList<>(candidates.values());
            sorted.sort(Comparator.comparingDouble(HybridCandidate::score).reversed());

            int written = 0;
            for (HybridCandidate candidate : sorted) {
                if (written >= topN) {
                    break;
                }
                writeRecommendation(userId.get(), candidate);
                written++;
            }
        }

        private double weightOf(String algorithmType) {
            if ("GENRE_PREFERENCE".equalsIgnoreCase(algorithmType)) {
                return 0.45D;
            }
            if ("USER_CF".equalsIgnoreCase(algorithmType)) {
                return 0.30D;
            }
            if ("ITEM_CF".equalsIgnoreCase(algorithmType)) {
                return 0.35D;
            }
            if ("HOT_SCORE".equalsIgnoreCase(algorithmType)) {
                return 0.20D;
            }
            return 0.10D;
        }

        private void writeRecommendation(long userId, HybridCandidate candidate) throws IOException {
            try {
                statement.setLong(1, userId);
                statement.setLong(2, candidate.movieId());
                statement.setString(3, TsvUtils.safe(candidate.title()));
                statement.setDouble(4, candidate.score());
                statement.setString(5, candidate.reason());
                statement.setString(6, "HYBRID");
                statement.executeUpdate();
            } catch (SQLException e) {
                throw new IOException("写入 HYBRID 推荐失败: userId=" + userId + ", movieId=" + candidate.movieId(), e);
            }
        }

        @Override
        protected void cleanup(Context context) throws IOException {
            try {
                if (statement != null) {
                    statement.close();
                }
                if (connection != null) {
                    connection.close();
                }
            } catch (SQLException e) {
                throw new IOException("关闭融合推荐 MySQL 连接失败", e);
            }
        }
    }

    private static final class HybridCandidate {
        private final long movieId;
        private final String title;
        private double score;
        private final List<String> algorithms = new ArrayList<>();
        private final List<String> reasons = new ArrayList<>();

        private HybridCandidate(long movieId, String title) {
            this.movieId = movieId;
            this.title = title;
        }

        private void addScore(double value, String algorithm, String reason) {
            this.score += value;
            if (algorithm != null && !algorithm.isBlank() && !algorithms.contains(algorithm)) {
                algorithms.add(algorithm);
            }
            if (reason != null && !reason.isBlank() && reasons.size() < 2) {
                reasons.add(reason);
            }
        }

        private long movieId() {
            return movieId;
        }

        private String title() {
            return title;
        }

        private double score() {
            return score;
        }

        private String reason() {
            String source = algorithms.isEmpty() ? "多路召回" : String.join("+", algorithms);
            String detail = reasons.isEmpty() ? "融合用户偏好、影片热度和相似口味生成" : String.join("；", reasons);
            return "融合推荐来源：" + source + "。" + detail;
        }
    }
}

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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 基于物品相似度的推荐 Job。
 *
 * <p>输入 TSV 格式：
 * {@code user_id \t rated_movie_id \t rated_rating \t candidate_movie_id \t candidate_title \t similarity \t co_occurrence \t candidate_hot_score}。
 * 适合结合离线共现矩阵结果做 MapReduce 推荐：同一用户高分看过的电影，会为相似影片投票，
 * 再叠加共现次数与影片热度作为置信度。</p>
 */
public class ItemCollaborativeFilteringJob {

    private final Configuration configuration;
    private final JobArguments arguments;

    public ItemCollaborativeFilteringJob(Configuration configuration, JobArguments arguments) {
        this.configuration = configuration;
        this.arguments = arguments;
    }

    public int run() throws Exception {
        Job job = Job.getInstance(configuration, "movie-item-cf-recommendation");
        job.setJarByClass(ItemCollaborativeFilteringJob.class);
        job.setMapperClass(ItemCfMapper.class);
        job.setReducerClass(ItemCfReducer.class);
        job.setMapOutputKeyClass(LongWritable.class);
        job.setMapOutputValueClass(Text.class);
        job.setOutputKeyClass(LongWritable.class);
        job.setOutputValueClass(Text.class);
        job.setNumReduceTasks(arguments.getInt("itemCfRecommendReducers", 1));
        job.getConfiguration().setInt("movie.analytics.recommendTopN", arguments.getInt("recommendTopN", 20));
        job.setOutputFormatClass(NullOutputFormat.class);
        FileInputFormat.addInputPath(job, new Path(arguments.get("itemCfRecommendInput", "/movie/mysql/item_cf_candidates")));
        return job.waitForCompletion(true) ? 0 : 1;
    }

    public static class ItemCfMapper extends Mapper<LongWritable, Text, LongWritable, Text> {
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

    public static class ItemCfReducer extends Reducer<LongWritable, Text, LongWritable, Text> {
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
                throw new IOException("初始化 ITEM_CF 推荐 MySQL 写入失败", e);
            }
        }

        @Override
        protected void reduce(LongWritable userId, Iterable<Text> values, Context context) throws IOException {
            Set<Long> ratedMovies = new HashSet<>();
            Map<Long, AggregatedCandidate> candidates = new HashMap<>();

            for (Text value : values) {
                String[] fields = TsvUtils.split(value.toString());
                long ratedMovieId = TsvUtils.parseLong(TsvUtils.value(fields, 1), -1L);
                double ratedRating = TsvUtils.parseDouble(TsvUtils.value(fields, 2), 0D);
                long candidateMovieId = TsvUtils.parseLong(TsvUtils.value(fields, 3), -1L);
                String candidateTitle = TsvUtils.value(fields, 4);
                double similarity = TsvUtils.parseDouble(TsvUtils.value(fields, 5), 0D);
                long coOccurrence = TsvUtils.parseLong(TsvUtils.value(fields, 6), 0L);
                double hotScore = TsvUtils.parseDouble(TsvUtils.value(fields, 7), 0D);

                if (ratedMovieId > 0) {
                    ratedMovies.add(ratedMovieId);
                }
                if (candidateMovieId <= 0 || ratedMovies.contains(candidateMovieId) || similarity <= 0D) {
                    continue;
                }

                AggregatedCandidate candidate = candidates.computeIfAbsent(
                        candidateMovieId,
                        key -> new AggregatedCandidate(candidateMovieId, candidateTitle)
                );
                candidate.addContribution(ratedMovieId, ratedRating, similarity, coOccurrence, hotScore);
            }

            List<AggregatedCandidate> sorted = new ArrayList<>(candidates.values());
            sorted.removeIf(candidate -> ratedMovies.contains(candidate.movieId()));
            sorted.sort(Comparator.comparingDouble(AggregatedCandidate::score).reversed());

            int written = 0;
            for (AggregatedCandidate candidate : sorted) {
                if (written >= topN) {
                    break;
                }
                writeRecommendation(userId.get(), candidate);
                written++;
            }
        }

        private void writeRecommendation(long userId, AggregatedCandidate candidate) throws IOException {
            try {
                statement.setLong(1, userId);
                statement.setLong(2, candidate.movieId());
                statement.setString(3, TsvUtils.safe(candidate.title()));
                statement.setDouble(4, candidate.score());
                statement.setString(5, candidate.reason());
                statement.setString(6, "ITEM_CF");
                statement.executeUpdate();
            } catch (SQLException e) {
                throw new IOException("写入 ITEM_CF 推荐失败: userId=" + userId + ", movieId=" + candidate.movieId(), e);
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
                throw new IOException("关闭 ITEM_CF 推荐 MySQL 连接失败", e);
            }
        }
    }

    private static final class AggregatedCandidate {
        private final long movieId;
        private final String title;
        private double score;
        private int supportCount;
        private final List<Long> sourceMovieIds = new ArrayList<>();

        private AggregatedCandidate(long movieId, String title) {
            this.movieId = movieId;
            this.title = title;
        }

        private void addContribution(long ratedMovieId, double ratedRating, double similarity, long coOccurrence, double hotScore) {
            double base = Math.max(ratedRating, 3D) * similarity * 20D;
            double confidence = Math.log1p(Math.max(coOccurrence, 0L)) * 3D;
            double popularity = Math.log1p(Math.max(hotScore, 0D));
            this.score += base + confidence + popularity;
            this.supportCount++;
            if (sourceMovieIds.size() < 3 && !sourceMovieIds.contains(ratedMovieId)) {
                sourceMovieIds.add(ratedMovieId);
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
            return "与你高分看过的影片在共现矩阵中相似，基于物品协同过滤召回；相似来源影片数=" + supportCount;
        }
    }
}

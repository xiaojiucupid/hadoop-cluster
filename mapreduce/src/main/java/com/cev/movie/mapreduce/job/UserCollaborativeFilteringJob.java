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
 * 用户协同过滤风格推荐 Job。
 *
 * <p>输入 TSV 格式：
 * {@code user_id \t rated_movie_id \t user_avg_rating \t candidate_movie_id \t candidate_title \t candidate_avg_rating \t candidate_rating_count}。
 * 该实现适合 MapReduce 演示：用用户平均评分与影片平均评分的接近程度近似“口味相似度”，
 * 再结合评分人数作为置信度，为用户召回未看过且均分接近其口味的电影。</p>
 */
public class UserCollaborativeFilteringJob {

    private final Configuration configuration;
    private final JobArguments arguments;

    public UserCollaborativeFilteringJob(Configuration configuration, JobArguments arguments) {
        this.configuration = configuration;
        this.arguments = arguments;
    }

    public int run() throws Exception {
        Job job = Job.getInstance(configuration, "movie-user-cf-recommendation");
        job.setJarByClass(UserCollaborativeFilteringJob.class);
        job.setMapperClass(CfMapper.class);
        job.setReducerClass(CfReducer.class);
        job.setMapOutputKeyClass(LongWritable.class);
        job.setMapOutputValueClass(Text.class);
        job.setOutputKeyClass(LongWritable.class);
        job.setOutputValueClass(Text.class);
        job.setNumReduceTasks(arguments.getInt("cfRecommendReducers", 1));
        job.getConfiguration().setInt("movie.analytics.recommendTopN", arguments.getInt("recommendTopN", 20));
        job.setOutputFormatClass(NullOutputFormat.class);
        FileInputFormat.addInputPath(job, new Path(arguments.get("cfRecommendInput", "/movie/mysql/cf_recommend_candidates")));
        return job.waitForCompletion(true) ? 0 : 1;
    }

    public static class CfMapper extends Mapper<LongWritable, Text, LongWritable, Text> {
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

    public static class CfReducer extends Reducer<LongWritable, Text, LongWritable, Text> {
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
                throw new IOException("初始化协同过滤推荐 MySQL 写入失败", e);
            }
        }

        @Override
        protected void reduce(LongWritable userId, Iterable<Text> values, Context context) throws IOException {
            Set<Long> ratedMovies = new HashSet<>();
            Map<Long, CandidateMovie> candidates = new HashMap<>();
            double userAvgRating = 0D;

            for (Text value : values) {
                String[] fields = TsvUtils.split(value.toString());
                long ratedMovieId = TsvUtils.parseLong(TsvUtils.value(fields, 1), -1L);
                userAvgRating = TsvUtils.parseDouble(TsvUtils.value(fields, 2), userAvgRating);
                long candidateMovieId = TsvUtils.parseLong(TsvUtils.value(fields, 3), -1L);
                String candidateTitle = TsvUtils.value(fields, 4);
                double candidateAvgRating = TsvUtils.parseDouble(TsvUtils.value(fields, 5), 0D);
                long candidateRatingCount = TsvUtils.parseLong(TsvUtils.value(fields, 6), 0L);

                if (ratedMovieId > 0) {
                    ratedMovies.add(ratedMovieId);
                }
                if (candidateMovieId > 0 && candidateAvgRating > 0D) {
                    candidates.put(candidateMovieId, new CandidateMovie(
                            candidateMovieId,
                            candidateTitle,
                            candidateAvgRating,
                            candidateRatingCount
                    ));
                }
            }

            List<RecommendationScore> scores = new ArrayList<>();
            for (CandidateMovie candidate : candidates.values()) {
                if (ratedMovies.contains(candidate.movieId())) {
                    continue;
                }
                double similarity = 1D / (1D + Math.abs(userAvgRating - candidate.avgRating()));
                double confidence = Math.log1p(candidate.ratingCount());
                double score = similarity * 100D + confidence;
                scores.add(new RecommendationScore(candidate, score));
            }
            scores.sort(Comparator.comparingDouble(RecommendationScore::score).reversed());

            int written = 0;
            for (RecommendationScore score : scores) {
                if (written >= topN) {
                    break;
                }
                writeRecommendation(userId.get(), score);
                written++;
            }
        }

        private void writeRecommendation(long userId, RecommendationScore score) throws IOException {
            CandidateMovie candidate = score.movie();
            try {
                statement.setLong(1, userId);
                statement.setLong(2, candidate.movieId());
                statement.setString(3, TsvUtils.safe(candidate.title()));
                statement.setDouble(4, score.score());
                statement.setString(5, "该影片平均评分与你的历史评分口味接近，并具有一定评分人数支撑");
                statement.setString(6, "USER_CF");
                statement.executeUpdate();
            } catch (SQLException e) {
                throw new IOException("写入 USER_CF 推荐失败: userId=" + userId + ", movieId=" + candidate.movieId(), e);
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
                throw new IOException("关闭协同过滤推荐 MySQL 连接失败", e);
            }
        }
    }

    private record CandidateMovie(long movieId, String title, double avgRating, long ratingCount) {
    }

    private record RecommendationScore(CandidateMovie movie, double score) {
    }
}

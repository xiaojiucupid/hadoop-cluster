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
 * 基于用户题材偏好的个性化推荐 Job。
 *
 * <p>输入需要由 MySQL 导出为宽表 TSV：
 * {@code user_id \t rated_movie_id \t rating \t candidate_movie_id \t candidate_title \t candidate_genre \t candidate_hot_score}。
 * Mapper 以 user_id 分组，Reducer 汇总用户高评分题材偏好后，对候选电影计算推荐分并写入
 * MySQL {@code recommendation_result}。这是适合毕设展示的可解释推荐算法，后续可升级协同过滤。</p>
 */
public class UserGenrePreferenceJob {

    private final Configuration configuration;
    private final JobArguments arguments;

    public UserGenrePreferenceJob(Configuration configuration, JobArguments arguments) {
        this.configuration = configuration;
        this.arguments = arguments;
    }

    public int run() throws Exception {
        try (Connection connection = MysqlWriters.getConnection(MysqlConfig.from(configuration))) {
            MysqlWriters.prepareRecommendation(connection);
        }
        Job job = Job.getInstance(configuration, "movie-user-genre-recommendation");
        job.setJarByClass(UserGenrePreferenceJob.class);
        job.setMapperClass(RecommendMapper.class);
        job.setReducerClass(RecommendReducer.class);
        job.setMapOutputKeyClass(LongWritable.class);
        job.setMapOutputValueClass(Text.class);
        job.setOutputKeyClass(LongWritable.class);
        job.setOutputValueClass(Text.class);
        job.setNumReduceTasks(arguments.getInt("recommendReducers", 1));
        job.getConfiguration().setInt("movie.analytics.recommendTopN", arguments.getInt("recommendTopN", 20));
        job.setOutputFormatClass(NullOutputFormat.class);
        FileInputFormat.addInputPath(job, new Path(arguments.get("recommendInput", "/movie/mysql/recommend_candidates")));
        return job.waitForCompletion(true) ? 0 : 1;
    }

    public static class RecommendMapper extends Mapper<LongWritable, Text, LongWritable, Text> {
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

    public static class RecommendReducer extends Reducer<LongWritable, Text, LongWritable, Text> {
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
                throw new IOException("初始化 recommendation_result MySQL 写入失败", e);
            }
        }

        @Override
        protected void reduce(LongWritable userId, Iterable<Text> values, Context context) throws IOException {
            Map<String, Double> genrePreference = new HashMap<>();
            Set<Long> ratedMovies = new HashSet<>();
            Map<Long, CandidateMovie> candidates = new HashMap<>();

            for (Text value : values) {
                String[] fields = TsvUtils.split(value.toString());
                long ratedMovieId = TsvUtils.parseLong(TsvUtils.value(fields, 1), -1L);
                double rating = TsvUtils.parseDouble(TsvUtils.value(fields, 2), 0D);
                long candidateMovieId = TsvUtils.parseLong(TsvUtils.value(fields, 3), -1L);
                String candidateTitle = TsvUtils.value(fields, 4);
                String candidateGenre = TsvUtils.value(fields, 5);
                double candidateHotScore = TsvUtils.parseDouble(TsvUtils.value(fields, 6), 0D);

                if (ratedMovieId > 0) {
                    ratedMovies.add(ratedMovieId);
                }
                if (!candidateGenre.isBlank() && rating >= 4D) {
                    genrePreference.merge(candidateGenre, rating, Double::sum);
                }
                if (candidateMovieId > 0 && !ratedMovies.contains(candidateMovieId)) {
                    candidates.put(candidateMovieId, new CandidateMovie(candidateMovieId, candidateTitle, candidateGenre, candidateHotScore));
                }
            }

            if (genrePreference.isEmpty() || candidates.isEmpty()) {
                return;
            }

            List<RecommendationScore> scores = new ArrayList<>();
            for (CandidateMovie candidate : candidates.values()) {
                if (ratedMovies.contains(candidate.movieId())) {
                    continue;
                }
                double preferenceScore = genrePreference.getOrDefault(candidate.genre(), 0D);
                if (preferenceScore <= 0D) {
                    continue;
                }
                double recommendScore = preferenceScore * 10D + Math.log1p(candidate.hotScore());
                scores.add(new RecommendationScore(candidate, recommendScore));
            }
            scores.sort(Comparator.comparingDouble(RecommendationScore::score).reversed());

            int written = 0;
            for (RecommendationScore score : scores) {
                if (written >= topN) {
                    break;
                }
                CandidateMovie movie = score.movie();
                try {
                    statement.setLong(1, userId.get());
                    statement.setLong(2, movie.movieId());
                    statement.setString(3, TsvUtils.safe(movie.title()));
                    statement.setDouble(4, score.score());
                    statement.setString(5, "你对“" + movie.genre() + "”类型电影评分较高，系统结合影片热度推荐");
                    statement.setString(6, "GENRE_PREFERENCE");
                    statement.executeUpdate();
                    written++;
                } catch (SQLException e) {
                    throw new IOException("写入 recommendation_result 失败: userId=" + userId + ", movieId=" + movie.movieId(), e);
                }
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
                throw new IOException("关闭 recommendation_result MySQL 连接失败", e);
            }
        }
    }

    private record CandidateMovie(long movieId, String title, String genre, double hotScore) {
    }

    private record RecommendationScore(CandidateMovie movie, double score) {
    }
}

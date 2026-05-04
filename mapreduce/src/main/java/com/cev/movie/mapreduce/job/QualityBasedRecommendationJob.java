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
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 基于质量分的推荐 Job。
 *
 * <p>输入 TSV 格式：
 * {@code user_id \t rated_movie_id \t candidate_movie_id \t candidate_title \t avg_rating \t rating_count \t comment_count \t release_year}。
 * 该算法结合贝叶斯均值、评分人数、评论数和影片新鲜度，适合为冷启动用户或高质量榜单场景提供推荐。</p>
 */
public class QualityBasedRecommendationJob {

    private static final String ALGORITHM_TYPE = "QUALITY_SCORE";

    private final Configuration configuration;
    private final JobArguments arguments;

    public QualityBasedRecommendationJob(Configuration configuration, JobArguments arguments) {
        this.configuration = configuration;
        this.arguments = arguments;
    }

    public int run() throws Exception {
        Job job = Job.getInstance(configuration, "movie-quality-score-recommendation");
        job.setJarByClass(QualityBasedRecommendationJob.class);
        job.setMapperClass(QualityMapper.class);
        job.setReducerClass(QualityReducer.class);
        job.setMapOutputKeyClass(LongWritable.class);
        job.setMapOutputValueClass(Text.class);
        job.setOutputKeyClass(LongWritable.class);
        job.setOutputValueClass(Text.class);
        job.setNumReduceTasks(arguments.getInt("qualityRecommendReducers", 1));
        job.getConfiguration().setInt("movie.analytics.recommendTopN", arguments.getInt("recommendTopN", 20));
        job.setOutputFormatClass(NullOutputFormat.class);
        FileInputFormat.addInputPath(job, new Path(arguments.get("qualityRecommendInput", "/movie/mysql/quality_recommend_candidates")));
        return job.waitForCompletion(true) ? 0 : 1;
    }

    public static class QualityMapper extends Mapper<LongWritable, Text, LongWritable, Text> {
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

    public static class QualityReducer extends Reducer<LongWritable, Text, LongWritable, Text> {
        private Connection connection;
        private PreparedStatement statement;
        private int topN;

        @Override
        protected void setup(Context context) throws IOException {
            topN = context.getConfiguration().getInt("movie.analytics.recommendTopN", 20);
            try {
                connection = MysqlWriters.getConnection(MysqlConfig.from(context.getConfiguration()));
                MysqlWriters.deleteRecommendationByAlgorithm(connection, ALGORITHM_TYPE);
                statement = MysqlWriters.recommendationStatement(connection);
            } catch (SQLException e) {
                throw new IOException("初始化 QUALITY_SCORE 推荐 MySQL 写入失败", e);
            }
        }

        @Override
        protected void reduce(LongWritable userId, Iterable<Text> values, Context context) throws IOException {
            Set<Long> ratedMovies = new HashSet<>();
            List<CandidateMovie> candidates = new ArrayList<>();
            int currentYear = java.time.LocalDate.now().getYear();

            for (Text value : values) {
                String[] fields = TsvUtils.split(value.toString());
                long ratedMovieId = TsvUtils.parseLong(TsvUtils.value(fields, 1), -1L);
                long candidateMovieId = TsvUtils.parseLong(TsvUtils.value(fields, 2), -1L);
                String candidateTitle = TsvUtils.value(fields, 3);
                double avgRating = TsvUtils.parseDouble(TsvUtils.value(fields, 4), 0D);
                long ratingCount = TsvUtils.parseLong(TsvUtils.value(fields, 5), 0L);
                long commentCount = TsvUtils.parseLong(TsvUtils.value(fields, 6), 0L);
                int releaseYear = (int) TsvUtils.parseLong(TsvUtils.value(fields, 7), 0L);

                if (ratedMovieId > 0) {
                    ratedMovies.add(ratedMovieId);
                }
                if (candidateMovieId > 0) {
                    double score = calculateQualityScore(avgRating, ratingCount, commentCount, releaseYear, currentYear);
                    candidates.add(new CandidateMovie(candidateMovieId, candidateTitle, score, avgRating, ratingCount, releaseYear));
                }
            }

            candidates.stream()
                    .filter(candidate -> !ratedMovies.contains(candidate.movieId()))
                    .sorted(Comparator.comparingDouble(CandidateMovie::score).reversed())
                    .limit(topN)
                    .forEach(candidate -> writeRecommendation(userId.get(), candidate));
        }

        private double calculateQualityScore(double avgRating, long ratingCount, long commentCount, int releaseYear, int currentYear) {
            double prior = 3.5D;
            double weight = ratingCount / (ratingCount + 50D);
            double bayesian = weight * avgRating + (1D - weight) * prior;
            double popularity = Math.log1p(Math.max(ratingCount, 0L)) * 1.8D + Math.log1p(Math.max(commentCount, 0L));
            double freshness = releaseYear > 0 ? Math.max(0D, 8D - (currentYear - releaseYear) * 0.25D) : 0D;
            return bayesian * 20D + popularity + freshness;
        }

        private void writeRecommendation(long userId, CandidateMovie candidate) {
            try {
                statement.setLong(1, userId);
                statement.setLong(2, candidate.movieId());
                statement.setString(3, TsvUtils.safe(candidate.title()));
                statement.setDouble(4, candidate.score());
                statement.setString(5, "影片综合质量较高：高均分(" + candidate.avgRating() + ")、较多评分人数(" + candidate.ratingCount() + ")");
                statement.setString(6, ALGORITHM_TYPE);
                statement.executeUpdate();
            } catch (SQLException e) {
                throw new IllegalStateException("写入 QUALITY_SCORE 推荐失败: userId=" + userId + ", movieId=" + candidate.movieId(), e);
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
                throw new IOException("关闭 QUALITY_SCORE 推荐 MySQL 连接失败", e);
            }
        }
    }

    private record CandidateMovie(long movieId, String title, double score, double avgRating, long ratingCount, int releaseYear) {
    }
}

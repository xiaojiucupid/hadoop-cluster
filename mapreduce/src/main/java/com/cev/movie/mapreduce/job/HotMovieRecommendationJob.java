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
 * 热门电影推荐 Job。
 *
 * <p>输入 TSV 格式：
 * {@code user_id \t rated_movie_id \t candidate_movie_id \t candidate_title \t candidate_hot_score}。
 * Reducer 会过滤用户已评分电影，为每个用户写入全局热度最高的 TopN 影片。
 * 该算法适合作为冷启动和召回兜底策略。</p>
 */
public class HotMovieRecommendationJob {

    private final Configuration configuration;
    private final JobArguments arguments;

    public HotMovieRecommendationJob(Configuration configuration, JobArguments arguments) {
        this.configuration = configuration;
        this.arguments = arguments;
    }

    public int run() throws Exception {
        Job job = Job.getInstance(configuration, "movie-hot-recommendation");
        job.setJarByClass(HotMovieRecommendationJob.class);
        job.setMapperClass(HotMapper.class);
        job.setReducerClass(HotReducer.class);
        job.setMapOutputKeyClass(LongWritable.class);
        job.setMapOutputValueClass(Text.class);
        job.setOutputKeyClass(LongWritable.class);
        job.setOutputValueClass(Text.class);
        job.setNumReduceTasks(arguments.getInt("hotRecommendReducers", 1));
        job.getConfiguration().setInt("movie.analytics.recommendTopN", arguments.getInt("recommendTopN", 20));
        job.setOutputFormatClass(NullOutputFormat.class);
        FileInputFormat.addInputPath(job, new Path(arguments.get("hotRecommendInput", "/movie/mysql/hot_recommend_candidates")));
        return job.waitForCompletion(true) ? 0 : 1;
    }

    public static class HotMapper extends Mapper<LongWritable, Text, LongWritable, Text> {
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

    public static class HotReducer extends Reducer<LongWritable, Text, LongWritable, Text> {
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
                throw new IOException("初始化热门推荐 MySQL 写入失败", e);
            }
        }

        @Override
        protected void reduce(LongWritable userId, Iterable<Text> values, Context context) throws IOException {
            Set<Long> ratedMovies = new HashSet<>();
            List<CandidateMovie> candidates = new ArrayList<>();

            for (Text value : values) {
                String[] fields = TsvUtils.split(value.toString());
                long ratedMovieId = TsvUtils.parseLong(TsvUtils.value(fields, 1), -1L);
                long candidateMovieId = TsvUtils.parseLong(TsvUtils.value(fields, 2), -1L);
                String candidateTitle = TsvUtils.value(fields, 3);
                double hotScore = TsvUtils.parseDouble(TsvUtils.value(fields, 4), 0D);

                if (ratedMovieId > 0) {
                    ratedMovies.add(ratedMovieId);
                }
                if (candidateMovieId > 0) {
                    candidates.add(new CandidateMovie(candidateMovieId, candidateTitle, hotScore));
                }
            }

            candidates.stream()
                    .filter(candidate -> !ratedMovies.contains(candidate.movieId()))
                    .sorted(Comparator.comparingDouble(CandidateMovie::hotScore).reversed())
                    .limit(topN)
                    .forEach(candidate -> writeRecommendation(userId.get(), candidate));
        }

        private void writeRecommendation(long userId, CandidateMovie candidate) {
            try {
                statement.setLong(1, userId);
                statement.setLong(2, candidate.movieId());
                statement.setString(3, TsvUtils.safe(candidate.title()));
                statement.setDouble(4, candidate.hotScore());
                statement.setString(5, "该影片近期评分和评论热度较高，可作为冷启动热门推荐");
                statement.setString(6, "HOT_SCORE");
                statement.executeUpdate();
            } catch (SQLException e) {
                throw new IllegalStateException("写入 HOT_SCORE 推荐失败: userId=" + userId + ", movieId=" + candidate.movieId(), e);
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
                throw new IOException("关闭热门推荐 MySQL 连接失败", e);
            }
        }
    }

    private record CandidateMovie(long movieId, String title, double hotScore) {
    }
}

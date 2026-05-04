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

/**
 * 热门电影汇总 Job。
 *
 * <p>输入来自 {@code vw_movie_core_metrics} 或等价 SQL 导出后的 HDFS TSV：
 * {@code movie_id \t name \t release_year \t avg_user_rating \t rating_count \t comment_count \t total_comment_votes}。
 * 热度分数公式：评分均值 * 20 + log(评分数+1) * 10 + log(评论数+1) * 5 + log(评论点赞+1)。</p>
 */
public class MovieHotScoreJob {

    private final Configuration configuration;
    private final JobArguments arguments;

    public MovieHotScoreJob(Configuration configuration, JobArguments arguments) {
        this.configuration = configuration;
        this.arguments = arguments;
    }

    public int run() throws Exception {
        try (Connection connection = MysqlWriters.getConnection(MysqlConfig.from(configuration))) {
            MysqlWriters.prepareMovieHot(connection);
        }
        Job job = Job.getInstance(configuration, "movie-hot-score");
        job.setJarByClass(MovieHotScoreJob.class);
        job.setMapperClass(HotMapper.class);
        job.setReducerClass(HotReducer.class);
        job.setMapOutputKeyClass(LongWritable.class);
        job.setMapOutputValueClass(Text.class);
        job.setOutputKeyClass(LongWritable.class);
        job.setOutputValueClass(Text.class);
        job.setNumReduceTasks(arguments.getInt("hotReducers", 1));
        job.setOutputFormatClass(NullOutputFormat.class);
        FileInputFormat.addInputPath(job, new Path(arguments.get("hotInput", "/movie/mysql/movie_core_metrics")));
        return job.waitForCompletion(true) ? 0 : 1;
    }

    public static class HotMapper extends Mapper<LongWritable, Text, LongWritable, Text> {
        private final LongWritable outKey = new LongWritable();
        private final Text outValue = new Text();

        @Override
        protected void map(LongWritable key, Text value, Context context) throws IOException, InterruptedException {
            String[] fields = TsvUtils.split(value.toString());
            long movieId = TsvUtils.parseLong(TsvUtils.value(fields, 0), -1L);
            if (movieId < 0) {
                return;
            }
            outKey.set(movieId);
            outValue.set(value.toString());
            context.write(outKey, outValue);
        }
    }

    public static class HotReducer extends Reducer<LongWritable, Text, LongWritable, Text> {
        private Connection connection;
        private PreparedStatement statement;

        @Override
        protected void setup(Context context) throws IOException {
            try {
                connection = MysqlWriters.getConnection(MysqlConfig.from(context.getConfiguration()));
                statement = MysqlWriters.movieHotStatement(connection);
            } catch (SQLException e) {
                throw new IOException("初始化 movie_summary MySQL 写入失败", e);
            }
        }

        @Override
        protected void reduce(LongWritable key, Iterable<Text> values, Context context) throws IOException {
            for (Text value : values) {
                String[] fields = TsvUtils.split(value.toString());
                String title = TsvUtils.value(fields, 1);
                long releaseYear = TsvUtils.parseLong(TsvUtils.value(fields, 2), 0L);
                double avgRating = TsvUtils.parseDouble(TsvUtils.value(fields, 3), 0D);
                long ratingCount = TsvUtils.parseLong(TsvUtils.value(fields, 4), 0L);
                long commentCount = TsvUtils.parseLong(TsvUtils.value(fields, 5), 0L);
                long totalCommentVotes = TsvUtils.parseLong(TsvUtils.value(fields, 6), 0L);
                double hotScore = avgRating * 20D + Math.log1p(ratingCount) * 10D + Math.log1p(commentCount) * 5D + Math.log1p(totalCommentVotes);
                try {
                    statement.setLong(1, key.get());
                    statement.setString(2, TsvUtils.safe(title));
                    if (releaseYear > 0) {
                        statement.setLong(3, releaseYear);
                    } else {
                        statement.setNull(3, java.sql.Types.INTEGER);
                    }
                    statement.setDouble(4, avgRating);
                    statement.setLong(5, ratingCount);
                    statement.setLong(6, commentCount);
                    statement.setDouble(7, hotScore);
                    statement.executeUpdate();
                } catch (SQLException e) {
                    throw new IOException("写入 movie_summary 失败: " + key, e);
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
                throw new IOException("关闭 movie_summary MySQL 连接失败", e);
            }
        }
    }
}

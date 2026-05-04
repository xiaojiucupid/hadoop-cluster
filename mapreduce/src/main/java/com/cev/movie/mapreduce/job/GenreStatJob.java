package com.cev.movie.mapreduce.job;

import com.cev.movie.mapreduce.JobArguments;
import com.cev.movie.mapreduce.common.MysqlConfig;
import com.cev.movie.mapreduce.common.MysqlWriters;
import com.cev.movie.mapreduce.common.TsvUtils;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.DoubleWritable;
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
 * 电影类型统计 Job。
 *
 * <p>输入来自 MySQL 表 {@code bridge_movie_genre} 与 {@code dim_movie} 导出后的 HDFS TSV：
 * {@code movie_id \t genre_name \t douban_score}，输出写入 MySQL {@code genre_stat}。</p>
 */
public class GenreStatJob {

    private final Configuration configuration;
    private final JobArguments arguments;

    public GenreStatJob(Configuration configuration, JobArguments arguments) {
        this.configuration = configuration;
        this.arguments = arguments;
    }

    public int run() throws Exception {
        try (Connection connection = MysqlWriters.getConnection(MysqlConfig.from(configuration))) {
            MysqlWriters.prepareGenreStat(connection);
        }
        Job job = Job.getInstance(configuration, "movie-genre-stat");
        job.setJarByClass(GenreStatJob.class);
        job.setMapperClass(GenreMapper.class);
        job.setReducerClass(GenreReducer.class);
        job.setMapOutputKeyClass(Text.class);
        job.setMapOutputValueClass(DoubleWritable.class);
        job.setOutputKeyClass(Text.class);
        job.setOutputValueClass(LongWritable.class);
        job.setOutputFormatClass(NullOutputFormat.class);
        FileInputFormat.addInputPath(job, new Path(arguments.get("genreInput", "/movie/mysql/genre_movie_score")));
        return job.waitForCompletion(true) ? 0 : 1;
    }

    public static class GenreMapper extends Mapper<LongWritable, Text, Text, DoubleWritable> {
        private final Text outKey = new Text();
        private final DoubleWritable outValue = new DoubleWritable();

        @Override
        protected void map(LongWritable key, Text value, Context context) throws IOException, InterruptedException {
            String[] fields = TsvUtils.split(value.toString());
            String genre = TsvUtils.value(fields, 1);
            if (genre.isBlank() || "genre_name".equalsIgnoreCase(genre)) {
                return;
            }
            double score = TsvUtils.parseDouble(TsvUtils.value(fields, 2), 0D);
            outKey.set(genre);
            outValue.set(score);
            context.write(outKey, outValue);
        }
    }

    public static class GenreReducer extends Reducer<Text, DoubleWritable, Text, LongWritable> {
        private Connection connection;
        private PreparedStatement statement;

        @Override
        protected void setup(Context context) throws IOException {
            try {
                connection = MysqlWriters.getConnection(MysqlConfig.from(context.getConfiguration()));
                statement = MysqlWriters.genreStatStatement(connection);
            } catch (SQLException e) {
                throw new IOException("初始化 genre_stat MySQL 写入失败", e);
            }
        }

        @Override
        protected void reduce(Text key, Iterable<DoubleWritable> values, Context context) throws IOException {
            long count = 0;
            double scoreSum = 0D;
            long scoredCount = 0;
            for (DoubleWritable value : values) {
                count++;
                if (value.get() > 0D) {
                    scoreSum += value.get();
                    scoredCount++;
                }
            }
            double avgScore = scoredCount == 0 ? 0D : scoreSum / scoredCount;
            try {
                statement.setString(1, key.toString());
                statement.setLong(2, count);
                statement.setDouble(3, avgScore);
                statement.executeUpdate();
            } catch (SQLException e) {
                throw new IOException("写入 genre_stat 失败: " + key, e);
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
                throw new IOException("关闭 genre_stat MySQL 连接失败", e);
            }
        }
    }
}

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
 * 基于标签偏好的内容推荐 Job。
 *
 * <p>输入 TSV 格式：
 * {@code user_id \t rated_movie_id \t rating \t candidate_movie_id \t candidate_title \t candidate_tags \t candidate_hot_score}。
 * 该算法从用户历史高分电影提取标签偏好，再对候选影片按标签重合度、热度进行排序，
 * 适合作为 Hadoop 毕设中的可解释内容召回算法。</p>
 */
public class TagPreferenceRecommendationJob {

    private static final String ALGORITHM_TYPE = "TAG_PREFERENCE";

    private final Configuration configuration;
    private final JobArguments arguments;

    public TagPreferenceRecommendationJob(Configuration configuration, JobArguments arguments) {
        this.configuration = configuration;
        this.arguments = arguments;
    }

    public int run() throws Exception {
        Job job = Job.getInstance(configuration, "movie-tag-preference-recommendation");
        job.setJarByClass(TagPreferenceRecommendationJob.class);
        job.setMapperClass(TagMapper.class);
        job.setReducerClass(TagReducer.class);
        job.setMapOutputKeyClass(LongWritable.class);
        job.setMapOutputValueClass(Text.class);
        job.setOutputKeyClass(LongWritable.class);
        job.setOutputValueClass(Text.class);
        job.setNumReduceTasks(arguments.getInt("tagRecommendReducers", 1));
        job.getConfiguration().setInt("movie.analytics.recommendTopN", arguments.getInt("recommendTopN", 20));
        job.setOutputFormatClass(NullOutputFormat.class);
        FileInputFormat.addInputPath(job, new Path(arguments.get("tagRecommendInput", "/movie/mysql/tag_recommend_candidates")));
        return job.waitForCompletion(true) ? 0 : 1;
    }

    public static class TagMapper extends Mapper<LongWritable, Text, LongWritable, Text> {
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

    public static class TagReducer extends Reducer<LongWritable, Text, LongWritable, Text> {
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
                throw new IOException("初始化 TAG_PREFERENCE 推荐 MySQL 写入失败", e);
            }
        }

        @Override
        protected void reduce(LongWritable userId, Iterable<Text> values, Context context) throws IOException {
            Map<String, Double> tagPreference = new HashMap<>();
            Set<Long> ratedMovies = new HashSet<>();
            Map<Long, CandidateMovie> candidates = new HashMap<>();

            for (Text value : values) {
                String[] fields = TsvUtils.split(value.toString());
                long ratedMovieId = TsvUtils.parseLong(TsvUtils.value(fields, 1), -1L);
                double rating = TsvUtils.parseDouble(TsvUtils.value(fields, 2), 0D);
                long candidateMovieId = TsvUtils.parseLong(TsvUtils.value(fields, 3), -1L);
                String candidateTitle = TsvUtils.value(fields, 4);
                String candidateTags = TsvUtils.value(fields, 5);
                double candidateHotScore = TsvUtils.parseDouble(TsvUtils.value(fields, 6), 0D);

                if (ratedMovieId > 0) {
                    ratedMovies.add(ratedMovieId);
                }
                if (rating >= 4D) {
                    accumulateTags(tagPreference, candidateTags, rating);
                }
                if (candidateMovieId > 0 && !ratedMovies.contains(candidateMovieId)) {
                    candidates.put(candidateMovieId, new CandidateMovie(candidateMovieId, candidateTitle, candidateTags, candidateHotScore));
                }
            }

            if (tagPreference.isEmpty() || candidates.isEmpty()) {
                return;
            }

            List<RecommendationScore> scores = new ArrayList<>();
            for (CandidateMovie candidate : candidates.values()) {
                if (ratedMovies.contains(candidate.movieId())) {
                    continue;
                }
                double tagScore = scoreTags(tagPreference, candidate.tags());
                if (tagScore <= 0D) {
                    continue;
                }
                double finalScore = tagScore * 8D + Math.log1p(candidate.hotScore());
                scores.add(new RecommendationScore(candidate, finalScore, topTags(tagPreference, candidate.tags())));
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

        private void accumulateTags(Map<String, Double> tagPreference, String rawTags, double rating) {
            for (String tag : splitBySlash(rawTags)) {
                if (!tag.isBlank()) {
                    tagPreference.merge(tag, rating, Double::sum);
                }
            }
        }

        private double scoreTags(Map<String, Double> tagPreference, String rawTags) {
            double score = 0D;
            for (String tag : splitBySlash(rawTags)) {
                score += tagPreference.getOrDefault(tag, 0D);
            }
            return score;
        }

        private String topTags(Map<String, Double> tagPreference, String rawTags) {
            List<String> matched = new ArrayList<>();
            for (String tag : splitBySlash(rawTags)) {
                if (tagPreference.containsKey(tag) && !matched.contains(tag)) {
                    matched.add(tag);
                }
                if (matched.size() >= 3) {
                    break;
                }
            }
            return matched.isEmpty() ? "标签偏好" : String.join("/", matched);
        }

        private List<String> splitBySlash(String raw) {
            List<String> result = new ArrayList<>();
            if (raw == null || raw.isBlank()) {
                return result;
            }
            for (String item : raw.split("/")) {
                String normalized = item == null ? "" : item.trim();
                if (!normalized.isBlank()) {
                    result.add(normalized);
                }
            }
            return result;
        }

        private void writeRecommendation(long userId, RecommendationScore score) throws IOException {
            CandidateMovie candidate = score.movie();
            try {
                statement.setLong(1, userId);
                statement.setLong(2, candidate.movieId());
                statement.setString(3, TsvUtils.safe(candidate.title()));
                statement.setDouble(4, score.score());
                statement.setString(5, "你偏好的标签包含：" + score.matchedTags() + "，系统结合影片热度进行内容推荐");
                statement.setString(6, ALGORITHM_TYPE);
                statement.executeUpdate();
            } catch (SQLException e) {
                throw new IOException("写入 TAG_PREFERENCE 推荐失败: userId=" + userId + ", movieId=" + candidate.movieId(), e);
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
                throw new IOException("关闭 TAG_PREFERENCE 推荐 MySQL 连接失败", e);
            }
        }
    }

    private record CandidateMovie(long movieId, String title, String tags, double hotScore) {
    }

    private record RecommendationScore(CandidateMovie movie, double score, String matchedTags) {
    }
}

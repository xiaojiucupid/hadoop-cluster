package com.cev.movie.mapreduce.common;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;

/**
 * MySQL 结果表写入工具。
 *
 * <p>这里集中维护建表、清空和 upsert SQL，方便 MapReduce Job 只关注计算逻辑。</p>
 */
public final class MysqlWriters {

    private MysqlWriters() {
    }

    public static Connection getConnection(MysqlConfig config) throws SQLException {
        return DriverManager.getConnection(config.url(), config.username(), config.password());
    }

    public static void prepareGenreStat(Connection connection) throws SQLException {
        execute(connection, "CREATE TABLE IF NOT EXISTS genre_stat ("
                + "id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,"
                + "genre_name VARCHAR(128) NOT NULL,"
                + "movie_count BIGINT NOT NULL DEFAULT 0,"
                + "avg_score DECIMAL(5,2) NULL,"
                + "updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,"
                + "UNIQUE KEY uk_genre_name (genre_name)"
                + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='MapReduce 电影类型统计结果表'");
        execute(connection, "TRUNCATE TABLE genre_stat");
    }

    public static void prepareMovieHot(Connection connection) throws SQLException {
        execute(connection, "CREATE TABLE IF NOT EXISTS movie_summary ("
                + "movie_id BIGINT NOT NULL PRIMARY KEY,"
                + "movie_title VARCHAR(255) NULL,"
                + "release_year INT NULL,"
                + "avg_rating DECIMAL(5,2) NULL,"
                + "rating_count BIGINT NOT NULL DEFAULT 0,"
                + "comment_count BIGINT NOT NULL DEFAULT 0,"
                + "hot_score DECIMAL(12,2) NOT NULL DEFAULT 0,"
                + "updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,"
                + "KEY idx_hot_score (hot_score)"
                + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='MapReduce 热门电影汇总表'");
        execute(connection, "TRUNCATE TABLE movie_summary");
    }

    public static void prepareRecommendation(Connection connection) throws SQLException {
        execute(connection, "CREATE TABLE IF NOT EXISTS recommendation_result ("
                + "id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,"
                + "user_id BIGINT NOT NULL,"
                + "movie_id BIGINT NOT NULL,"
                + "movie_title VARCHAR(255) NULL,"
                + "recommend_score DECIMAL(12,4) NOT NULL DEFAULT 0,"
                + "reason VARCHAR(500) NULL,"
                + "algorithm_type VARCHAR(32) NOT NULL DEFAULT 'GENRE_PREFERENCE',"
                + "calculated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,"
                + "UNIQUE KEY uk_user_movie_algo (user_id, movie_id, algorithm_type),"
                + "KEY idx_user_algo_score (user_id, algorithm_type, recommend_score)"
                + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='MapReduce 个性化推荐结果表'");
        execute(connection, "ALTER TABLE recommendation_result ADD COLUMN IF NOT EXISTS algorithm_type VARCHAR(32) NOT NULL DEFAULT 'GENRE_PREFERENCE'");
        execute(connection, "ALTER TABLE recommendation_result ADD COLUMN IF NOT EXISTS calculated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP");
    }

    public static void clearRecommendation(Connection connection) throws SQLException {
        execute(connection, "TRUNCATE TABLE recommendation_result");
    }

    public static void deleteRecommendationByAlgorithm(Connection connection, String algorithmType) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("DELETE FROM recommendation_result WHERE algorithm_type = ?")) {
            statement.setString(1, algorithmType);
            statement.executeUpdate();
        }
    }

    public static PreparedStatement genreStatStatement(Connection connection) throws SQLException {
        return connection.prepareStatement("INSERT INTO genre_stat (genre_name, movie_count, avg_score) VALUES (?, ?, ?)");
    }

    public static PreparedStatement movieHotStatement(Connection connection) throws SQLException {
        return connection.prepareStatement("INSERT INTO movie_summary (movie_id, movie_title, release_year, avg_rating, rating_count, comment_count, hot_score) VALUES (?, ?, ?, ?, ?, ?, ?)");
    }

    public static PreparedStatement recommendationStatement(Connection connection) throws SQLException {
        return connection.prepareStatement("INSERT INTO recommendation_result (user_id, movie_id, movie_title, recommend_score, reason, algorithm_type) VALUES (?, ?, ?, ?, ?, ?)");
    }

    private static void execute(Connection connection, String sql) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.execute();
        }
    }
}

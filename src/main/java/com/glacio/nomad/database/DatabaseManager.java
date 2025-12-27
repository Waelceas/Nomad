package com.glacio.nomad.database;

import com.glacio.nomad.Nomad;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;
import java.util.logging.Logger;

public class DatabaseManager {
    
    private final Nomad plugin;
    private Connection connection;
    private final Logger logger;
    
    public DatabaseManager(Nomad plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
    }
    
    public boolean connect() {
        try {
            // SQLite database file will be created in plugin data folder
            String dbPath = plugin.getDataFolder().getAbsolutePath() + "/database.db";
            String url = "jdbc:sqlite:" + dbPath;
            
            connection = DriverManager.getConnection(url);
            logger.info("SQLite database connected successfully!");
            
            // Create tables if they don't exist
            createTables();
            return true;
            
        } catch (SQLException e) {
            logger.severe("Failed to connect to SQLite database: " + e.getMessage());
            return false;
        }
    }
    
    public void disconnect() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
                logger.info("SQLite database disconnected!");
            }
        } catch (SQLException e) {
            logger.severe("Error disconnecting from database: " + e.getMessage());
        }
    }
    
    private void createTables() {
        try (Statement stmt = connection.createStatement()) {
            
            // Player purchases table
            String createPurchasesTable = """
                CREATE TABLE IF NOT EXISTS purchases (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    player_uuid TEXT NOT NULL,
                    player_name TEXT NOT NULL,
                    item_material TEXT NOT NULL,
                    item_name TEXT NOT NULL,
                    price REAL NOT NULL,
                    purchase_date TEXT NOT NULL,
                    server_name TEXT
                )
                """;
            
            // Player statistics table
            String createStatsTable = """
                CREATE TABLE IF NOT EXISTS player_stats (
                    player_uuid TEXT PRIMARY KEY,
                    player_name TEXT NOT NULL,
                    total_purchases INTEGER DEFAULT 0,
                    total_spent REAL DEFAULT 0.0,
                    first_purchase_date TEXT,
                    last_purchase_date TEXT
                )
                """;
            
            // Item popularity table
            String createPopularityTable = """
                CREATE TABLE IF NOT EXISTS item_popularity (
                    item_material TEXT PRIMARY KEY,
                    item_name TEXT NOT NULL,
                    times_purchased INTEGER DEFAULT 0,
                    total_revenue REAL DEFAULT 0.0,
                    last_purchased TEXT
                )
                """;
            
            stmt.execute(createPurchasesTable);
            stmt.execute(createStatsTable);
            stmt.execute(createPopularityTable);
            
            logger.info("Database tables created/verified successfully!");
            
        } catch (SQLException e) {
            logger.severe("Error creating database tables: " + e.getMessage());
        }
    }
    
    public void recordPurchase(UUID playerUuid, String playerName, String itemMaterial, 
                             String itemName, double price) {
        try {
            String currentDate = java.time.LocalDateTime.now().toString();
            String serverName = plugin.getServer().getName() != null ? plugin.getServer().getName() : "unknown";
            
            // Insert purchase record
            String insertPurchase = """
                INSERT INTO purchases (player_uuid, player_name, item_material, item_name, price, purchase_date, server_name)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """;
            
            try (PreparedStatement pstmt = connection.prepareStatement(insertPurchase)) {
                pstmt.setString(1, playerUuid.toString());
                pstmt.setString(2, playerName);
                pstmt.setString(3, itemMaterial);
                pstmt.setString(4, itemName);
                pstmt.setDouble(5, price);
                pstmt.setString(6, currentDate);
                pstmt.setString(7, serverName);
                pstmt.executeUpdate();
            }
            
            // Update player statistics
            updatePlayerStats(playerUuid, playerName, price, currentDate);
            
            // Update item popularity
            updateItemPopularity(itemMaterial, itemName, price, currentDate);
            
            logger.info("Purchase recorded: " + playerName + " bought " + itemName + " for " + price);
            
        } catch (SQLException e) {
            logger.severe("Error recording purchase: " + e.getMessage());
        }
    }
    
    private void updatePlayerStats(UUID playerUuid, String playerName, double price, String date) throws SQLException {
        String checkPlayer = "SELECT player_uuid FROM player_stats WHERE player_uuid = ?";
        
        try (PreparedStatement pstmt = connection.prepareStatement(checkPlayer)) {
            pstmt.setString(1, playerUuid.toString());
            ResultSet rs = pstmt.executeQuery();
            
            if (rs.next()) {
                // Update existing player stats
                String updateStats = """
                    UPDATE player_stats 
                    SET player_name = ?, total_purchases = total_purchases + 1, 
                        total_spent = total_spent + ?, last_purchase_date = ?
                    WHERE player_uuid = ?
                    """;
                
                try (PreparedStatement updateStmt = connection.prepareStatement(updateStats)) {
                    updateStmt.setString(1, playerName);
                    updateStmt.setDouble(2, price);
                    updateStmt.setString(3, date);
                    updateStmt.setString(4, playerUuid.toString());
                    updateStmt.executeUpdate();
                }
            } else {
                // Insert new player stats
                String insertStats = """
                    INSERT INTO player_stats (player_uuid, player_name, total_purchases, total_spent, first_purchase_date, last_purchase_date)
                    VALUES (?, ?, 1, ?, ?, ?)
                    """;
                
                try (PreparedStatement insertStmt = connection.prepareStatement(insertStats)) {
                    insertStmt.setString(1, playerUuid.toString());
                    insertStmt.setString(2, playerName);
                    insertStmt.setDouble(3, price);
                    insertStmt.setString(4, date);
                    insertStmt.setString(5, date);
                    insertStmt.executeUpdate();
                }
            }
        }
    }
    
    private void updateItemPopularity(String itemMaterial, String itemName, double price, String date) throws SQLException {
        String checkItem = "SELECT item_material FROM item_popularity WHERE item_material = ?";
        
        try (PreparedStatement pstmt = connection.prepareStatement(checkItem)) {
            pstmt.setString(1, itemMaterial);
            ResultSet rs = pstmt.executeQuery();
            
            if (rs.next()) {
                // Update existing item popularity
                String updatePopularity = """
                    UPDATE item_popularity 
                    SET times_purchased = times_purchased + 1, total_revenue = total_revenue + ?, last_purchased = ?
                    WHERE item_material = ?
                    """;
                
                try (PreparedStatement updateStmt = connection.prepareStatement(updatePopularity)) {
                    updateStmt.setDouble(1, price);
                    updateStmt.setString(2, date);
                    updateStmt.setString(3, itemMaterial);
                    updateStmt.executeUpdate();
                }
            } else {
                // Insert new item popularity
                String insertPopularity = """
                    INSERT INTO item_popularity (item_material, item_name, times_purchased, total_revenue, last_purchased)
                    VALUES (?, ?, 1, ?, ?)
                    """;
                
                try (PreparedStatement insertStmt = connection.prepareStatement(insertPopularity)) {
                    insertStmt.setString(1, itemMaterial);
                    insertStmt.setString(2, itemName);
                    insertStmt.setDouble(3, price);
                    insertStmt.setString(4, date);
                    insertStmt.executeUpdate();
                }
            }
        }
    }
    
    public ResultSet getPlayerPurchases(UUID playerUuid) throws SQLException {
        String query = """
            SELECT item_material, item_name, price, purchase_date 
            FROM purchases 
            WHERE player_uuid = ? 
            ORDER BY purchase_date DESC
            """;
        
        PreparedStatement pstmt = connection.prepareStatement(query);
        pstmt.setString(1, playerUuid.toString());
        return pstmt.executeQuery();
    }
    
    public ResultSet getTopItems(int limit) throws SQLException {
        String query = """
            SELECT item_material, item_name, times_purchased, total_revenue 
            FROM item_popularity 
            ORDER BY times_purchased DESC 
            LIMIT ?
            """;
        
        PreparedStatement pstmt = connection.prepareStatement(query);
        pstmt.setInt(1, limit);
        return pstmt.executeQuery();
    }
    
    public ResultSet getTopSpenders(int limit) throws SQLException {
        String query = """
            SELECT player_name, total_purchases, total_spent 
            FROM player_stats 
            WHERE total_spent > 0 
            ORDER BY total_spent DESC 
            LIMIT ?
            """;
        
        PreparedStatement pstmt = connection.prepareStatement(query);
        pstmt.setInt(1, limit);
        return pstmt.executeQuery();
    }
    
    public Connection getConnection() {
        return connection;
    }
}

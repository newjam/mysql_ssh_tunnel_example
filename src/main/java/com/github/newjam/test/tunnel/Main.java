package com.github.newjam.test.tunnel;

import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Main {
  
  private final static Logger log = LoggerFactory.getLogger(Main.class);
  
  public static void main(String...args) throws IOException, ClassNotFoundException, SQLException {
    Properties props = new Properties();
    props.load(Main.class.getClassLoader().getResourceAsStream("application.properties"));
    ExecutorService service = Executors.newSingleThreadExecutor();
    
    try(Tunnel tunnel = new Tunnel(props)) {
    
      service.execute(tunnel);

      tunnel.waitForConnection();

      Class.forName("com.mysql.jdbc.Driver");

      final String DATABASE_URL = props.getProperty("DATABASE_URL");
      final String DATABASE_USERNAME = props.getProperty("DATABASE_USERNAME");
      final String DATABASE_PASSWORD = props.getProperty("DATABASE_PASSWORD");

      Connection conn = DriverManager.getConnection(DATABASE_URL, DATABASE_USERNAME, DATABASE_PASSWORD);

      Statement stmt = conn.createStatement();

      final String APPROVED_CLIP_COUNT_QUERY = "SELECT COUNT(*) AS approved_clip_count FROM clip WHERE status = 'A'";

      ResultSet result = stmt.executeQuery(APPROVED_CLIP_COUNT_QUERY);
      result.beforeFirst();
      result.next();

      long approvedClipCount = result.getLong("approved_clip_count");

      log.debug("There are {} approved clips.", approvedClipCount);
    
    } finally {
      service.shutdownNow();
    }
    
  }
}

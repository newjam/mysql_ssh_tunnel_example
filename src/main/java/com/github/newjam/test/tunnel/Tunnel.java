package com.github.newjam.test.tunnel;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.security.PublicKey;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;
import net.schmizz.sshj.SSHClient;
import net.schmizz.sshj.connection.channel.direct.LocalPortForwarder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Tunnel {
  
  private final static CountDownLatch tunnelEstablishedLatch = new CountDownLatch(1);
  
  private final static Logger log = LoggerFactory.getLogger(Tunnel.class);
  
  private final static Properties applicationProperties = new Properties();
  
  
  private static String SSH_USERNAME;
  private static String SSH_PASSWORD;
  private static int REMOTE_PORT;
  private static int LOCAL_PORT;
  private static String DATABASE_USERNAME;
  private static String DATABASE_PASSWORD;
  private static String REMOTE_HOST;
  private static String DATABASE_URL;
  
  
  static {
    try {
      applicationProperties.load(Tunnel.class.getResourceAsStream("application.properties"));
      
      SSH_USERNAME = applicationProperties.getProperty("SSH_USERNAME");
      SSH_PASSWORD = applicationProperties.getProperty("SSH_PASSWORD");
      REMOTE_PORT = Integer.parseInt(applicationProperties.getProperty("REMOTE_PORT"));
      LOCAL_PORT = Integer.parseInt(applicationProperties.getProperty("LOCAL_PORT"));
      DATABASE_USERNAME = applicationProperties.getProperty("DATABASE_USERNAME");
      DATABASE_PASSWORD = applicationProperties.getProperty("DATABASE_PASSWORD");
      REMOTE_HOST = applicationProperties.getProperty("REMOTE_HOST");
      DATABASE_URL = applicationProperties.getProperty("DATABASE_URL");
    } catch (IOException ex) {
      log.error("", ex);
    }
  }
  
  public static boolean verify(String string, int i, PublicKey pk) {
    return true;
  }
  
  public static void tunnel() {
    try {
      SSHClient ssh = new SSHClient();
      try {
        ssh.addHostKeyVerifier(Tunnel::verify);
        ssh.connect(REMOTE_HOST);
        ssh.authPassword(SSH_USERNAME, SSH_PASSWORD);
        
        log.debug("SSH connection {} authenticated", ssh.isAuthenticated() ? "is" : "is not");
        
        final LocalPortForwarder.Parameters params
          = new LocalPortForwarder.Parameters("127.0.0.1", LOCAL_PORT, "127.0.0.1", REMOTE_PORT);

        final ServerSocket ss = new ServerSocket();
        ss.setReuseAddress(true);
        ss.bind(new InetSocketAddress(params.getLocalHost(), params.getLocalPort()));

        try {
          LocalPortForwarder forwarder = ssh.newLocalPortForwarder(params, ss);
          tunnelEstablishedLatch.countDown();
          forwarder.listen();
        } finally {
          log.debug("Stopping port forwarder.");
          ss.close();
        }
      } finally {
        log.debug("Disconnecting ssh.");
        ssh.disconnect();
        ssh.close();
      }
    }catch(IOException ex) {
      log.error("Error tunneling", ex);
    }
  }
  
  public static void main(String...args) throws ClassNotFoundException, SQLException, InterruptedException {
    
    Thread tunneler = new Thread(Tunnel::tunnel);
    tunneler.start();
    
    tunnelEstablishedLatch.await();
    Thread.sleep(100);
    
    Class.forName("com.mysql.jdbc.Driver");
    
    Connection conn = DriverManager.getConnection(DATABASE_URL, DATABASE_USERNAME, DATABASE_PASSWORD);
    
    Statement stmt = conn.createStatement();
    
    final String APPROVED_CLIP_COUNT_QUERY = "SELECT COUNT(*) AS approved_clip_count FROM clip WHERE status = 'A'";
    
    ResultSet result = stmt.executeQuery(APPROVED_CLIP_COUNT_QUERY);
    result.beforeFirst();
    result.next();
    
    long approvedClipCount = result.getLong("approved_clip_count");
    
    log.debug("There are {} approved clips.", approvedClipCount);
    
    tunneler.stop();
    
  }
  
}

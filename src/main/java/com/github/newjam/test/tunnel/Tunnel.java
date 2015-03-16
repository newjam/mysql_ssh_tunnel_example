package com.github.newjam.test.tunnel;

import java.io.Closeable;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.security.PublicKey;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;
import net.schmizz.sshj.SSHClient;
import net.schmizz.sshj.connection.channel.direct.LocalPortForwarder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Tunnel implements Runnable, Closeable {
  
  private final static Logger log = LoggerFactory.getLogger(Tunnel.class);
  
  private final CountDownLatch tunnelEstablishedLatch = new CountDownLatch(1);
  
  private final String SSH_USERNAME;
  private final String SSH_PASSWORD;
  private final String REMOTE_HOST;
  private final int REMOTE_PORT;
  private final int LOCAL_PORT;
  
  private SSHClient ssh = null;
  private ServerSocket ss = null;

  public Tunnel(Properties props) {
    this(
        props.getProperty("REMOTE_HOST")
      , props.getProperty("SSH_USERNAME")
      , props.getProperty("SSH_PASSWORD")
      , Integer.parseInt(props.getProperty("REMOTE_PORT"))
      , Integer.parseInt(props.getProperty("LOCAL_PORT"))
    );
  }
  
  private Tunnel(
      String remotehost
    , String username
    , String password
    , int remotePort
    , int localPort
  ) {
    this.SSH_USERNAME = username;
    this.SSH_PASSWORD = password;
    this.REMOTE_HOST = remotehost;
    this.REMOTE_PORT = remotePort;
    this.LOCAL_PORT = localPort;
  }
  
  private static boolean hostKeyVerify(String string, int i, PublicKey pk) {
    return true;
  }

  public void waitForConnection() {
    try {
      tunnelEstablishedLatch.await();
      Thread.sleep(50);
    } catch (InterruptedException ex) {
      log.debug("Error waiting for connection to be established.", ex);
    }
  }
 
  @Override
  public void run() {
    try {
      log.debug("Opening {}", this);
      
      ssh = new SSHClient();
      ss = new ServerSocket();

      ssh.addHostKeyVerifier(Tunnel::hostKeyVerify);
      ssh.connect(REMOTE_HOST);
      ssh.authPassword(SSH_USERNAME, SSH_PASSWORD);

      log.debug("SSH connection {} authenticated", ssh.isAuthenticated() ? "is" : "is not");

      final LocalPortForwarder.Parameters params
        = new LocalPortForwarder.Parameters("127.0.0.1", LOCAL_PORT, "127.0.0.1", REMOTE_PORT);

      ss.setReuseAddress(true);
      ss.bind(new InetSocketAddress(params.getLocalHost(), params.getLocalPort()));

      LocalPortForwarder forwarder = ssh.newLocalPortForwarder(params, ss);
      
      tunnelEstablishedLatch.countDown();
      forwarder.listen();
    } catch (IOException ex) {
      log.error("Error establishing {}.", this, ex);
    } finally {
      log.debug("Terminated {}", this);
    }
  }

  @Override
  public String toString() {
    return "Tunnel from localhost:" + LOCAL_PORT + " to " + SSH_USERNAME + "@" + REMOTE_HOST + ":" + REMOTE_PORT;
  }
  
  @Override
  public void close() throws IOException {
    if(ssh != null || ss != null) {
      log.debug("Closing {}", this);
    } else {
      log.warn("{} is already closed.", this);
    }
    
    if(ssh != null) {
      log.debug("Closing SSHClient.");
      if(ssh.isConnected()) {
        ssh.disconnect();
      }
      ssh.close();
      ssh = null;
    }
    
    if(ss != null) {
      log.debug("Closing ServerSocket.");
      ss.close();
      ss = null;
    }
    
  }
  
}

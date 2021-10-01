package org.rapturemain.tcpmessengerserver;


import ch.qos.logback.classic.Level;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@Slf4j
public class TcpMessengerServerApplication {

    public static void main(String[] args) throws InterruptedException {
//        setLoggingLevel(Level.DEBUG);
        SpringApplication.run(TcpMessengerServerApplication.class, args);
//        setLoggingLevel(Level.DEBUG);
    }

    public static void setLoggingLevel(ch.qos.logback.classic.Level level) {
        ch.qos.logback.classic.Logger root = (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(ch.qos.logback.classic.Logger.ROOT_LOGGER_NAME);
        root.setLevel(level);
    }

}

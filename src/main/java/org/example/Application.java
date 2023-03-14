package org.example;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.time.Instant;

@SpringBootApplication
@EnableTransactionManagement
public class Application {

  public static void main(String[] args) {
    SpringApplication.run(Application.class, args);
  }

  @Bean
  public ApplicationRunner runner(KafkaTemplate<String, String> template) {
    return args -> template.executeInTransaction(t -> t.send("topic1", "Test " + Instant.now()));
  }

  @Bean
  public DataSourceTransactionManager dstm(DataSource dataSource) {
    return new DataSourceTransactionManager(dataSource);
  }

  @Component
  public static class Listener {

    private final JdbcTemplate jdbcTemplate;

    private final KafkaTemplate<String, String> kafkaTemplate;

    public Listener(JdbcTemplate jdbcTemplate, KafkaTemplate<String, String> kafkaTemplate) {
      this.jdbcTemplate = jdbcTemplate;
      this.kafkaTemplate = kafkaTemplate;
    }

    @KafkaListener(id = "group1", topics = "topic1")
    @Transactional("dstm")
    public void listen1(String in) throws Exception {
      System.out.println("listen1");
      System.out.println(in);
      this.kafkaTemplate.send("topic2", in.toUpperCase());
      this.jdbcTemplate.execute("insert into mytable (data) values ('" + in + "')");
      throw new RuntimeException("rollback?");
    }

    @KafkaListener(id = "group2", topics = "topic2")
    public void listen2(String in) {
      System.out.println("listen2");
      System.out.println(in);
    }

  }

  @Bean
  public NewTopic topic1() {
    return TopicBuilder.name("topic1").build();
  }

  @Bean
  public NewTopic topic2() {
    return TopicBuilder.name("topic2").build();
  }

}

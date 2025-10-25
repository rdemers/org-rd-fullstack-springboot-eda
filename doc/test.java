package org;

import java.io.IOException;

import org.junit.jupiter.api.Test;

public class test {

@Test
public void whenGeneratingUUIDUsingNewJPAGenerationType_thenHibernateGeneratedUUID() throws IOException {
    Reservation reservation = new Reservation();
    reservation.setStatus("created");
    reservation.setNumber("12345");
    UUID saved = (UUID) session.save(reservation);
    Assertions.assertThat(saved).isNotNull();
}
@Test
public void whenGeneratingUUIDUsingNewJPAGenerationType_thenHibernateGeneratedUUIDOfVersion4() throws IOException {
    Reservation reservation = new Reservation();
    reservation.setStatus("new");
    reservation.setNumber("012");
    UUID saved = (UUID) session.save(reservation);
    Assertions.assertThat(saved).isNotNull();
    Assertions.assertThat(saved.version()).isEqualTo(4);
}    



}

/*
 * @Service
public class KafkaProducer {

    private final KafkaTemplate<String, String> kafkaTemplate;

    public KafkaProducer(KafkaTemplate<String, String> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void sendMessage(String topic, String message) {
        kafkaTemplate.send(topic, message);
    }
}
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class KafkaConsumer {

    @KafkaListener(topics = "my-topic", groupId = "my-group")
    public void listen(String message) {
        System.out.println("Message reçu : " + message);
    }


 * @Id @GeneratedValue
private UUID id;
if you want the UUID to be generated.

However, note that, by default, this maps to a column of type UUID if the database has such a type. If you really need to map to a VARCHAR columns, use:

@Id @GeneratedValue
@JdbcTypeCode(Types.VARCHAR)
private UUID id;
or:

@Id @GeneratedValue
@JdbcType(VarcharJdbcType.class)
private UUID id;
 */

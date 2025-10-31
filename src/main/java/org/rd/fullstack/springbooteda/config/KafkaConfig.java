/*
 * Copyright 2025; Réal Demers.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.rd.fullstack.springbooteda.config;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.rd.fullstack.springbooteda.util.KafkaSandbox;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;

@EnableKafka
@Configuration
public class KafkaConfig {

    public KafkaConfig() {
        super();
    }

    @Bean
    public KafkaSandbox getKafkaToolBox() {
        return new KafkaSandbox();
    }

    //@Bean
    public ProducerFactory<String, String> producerFactory() {       
        return new DefaultKafkaProducerFactory<String, String>(producerConfigs());
    }

    @Bean
    public Map<String, Object> producerConfigs() {
        // For more properties, see : https://kafka.apache.org/documentation/#producerconfigs 
        Map<String, Object> props = new HashMap<>();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        
        return props;
    }

    //@Bean
    public KafkaTemplate<String, String> kafkaTemplate() {
        return new KafkaTemplate<String, String>(producerFactory());
    }

    //@Bean
    public KafkaTemplate<String, String> stringTemplate(ProducerFactory<String, String> pf) {
        return new KafkaTemplate<>(pf);
    }

    //@Bean
    public KafkaTemplate<String, byte[]> bytesTemplate(ProducerFactory<String, byte[]> pf) {
        return new KafkaTemplate<>(pf,
            Collections.singletonMap(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class));
    }

    public void sendToKafka(final String data) {
        final ProducerRecord<String, String> record = createRecord(data);
//
  //      try {
            //kafkaTemplate().send(record).get(10, TimeUnit.SECONDS);
            int l = 0;
        handleSuccess(data);
    //}
    //catch (ExecutionException e) {
        //handleFailure(data, record, e.getCause());
    //}
    //catch (TimeoutException | InterruptedException e) {
        //handleFailure(data);
    //}
}

    private void handleSuccess(String data) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'handleSuccess'");
    }

    private ProducerRecord<String, String> createRecord(String data) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'createRecord'");
    }
// https://docs.spring.io/spring-kafka/reference/kafka/sending-messages.html
}
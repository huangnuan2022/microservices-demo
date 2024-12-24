package com.microservices.demo.kafka.admin.client;

import com.microservices.demo.config.KafkaConfigData;
import com.microservices.demo.config.RetryConfigData;
import com.microservices.demo.kafka.admin.exception.KafkaClientException;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.CreateTopicsResult;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.admin.TopicListing;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.retry.RetryContext;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

@Component
public class KafkaAdminClient {

    private static final Logger LOG = LoggerFactory.getLogger(KafkaAdminClient.class);

    private final KafkaConfigData kafkaConfigData;
    private final RetryConfigData retryConfigData;

    private final AdminClient adminClient;
    private final WebClient webClient;

    private final RetryTemplate retryTemplate;

    public KafkaAdminClient(KafkaConfigData kafkaConfigData, RetryConfigData retryConfigData, AdminClient adminClient, WebClient webClient, RetryTemplate retryTemplate) {
        this.kafkaConfigData = kafkaConfigData;
        this.retryConfigData = retryConfigData;
        this.adminClient = adminClient;
        this.webClient = webClient;
        this.retryTemplate = retryTemplate;
    }

    public void createTopics() {
        CreateTopicsResult createTopicsResult;
        try {
            createTopicsResult = retryTemplate.execute(this::doCreateTopics);
            LOG.info("Create topic result {}", createTopicsResult.values().values());
        } catch (Throwable t) {
            throw new KafkaClientException("Reached max number of retry for creating kafka topic(s)!", t);
        }
        checkTopicsCreated();
    }


    private CreateTopicsResult doCreateTopics(RetryContext retryContext){
        List<String> topicNames = kafkaConfigData.getTopicNamesToCreate();
        LOG.info("Creating {} topic(s), attempt {}", topicNames, retryContext.getRetryCount());
        List<NewTopic> kafkaTopics = topicNames.stream().map(topic -> new NewTopic(topic.trim(), kafkaConfigData.getNumOfPartitions(), kafkaConfigData.getReplicationFactor()))
                .collect(Collectors.toList());
        return adminClient.createTopics(kafkaTopics);
    }

    public void checkTopicsCreated(){
        Collection<TopicListing> topics = getTopics();
        int retryCount = 1;
        Integer maxRetry = retryConfigData.getMaxAttempts();
        Long sleepTimes = retryConfigData.getSleepTimeMs();
        int multiplier = retryConfigData.getMultiplier().intValue();
        for (String topic : kafkaConfigData.getTopicNamesToCreate() ){
            while(!isTopicCreated(topics, topic)){
                checkMaxRetry(retryCount++, maxRetry );
                sleep(sleepTimes);
                sleepTimes *= multiplier;
                topics = getTopics();
            }
        }


    }

    private boolean isTopicCreated(Collection<TopicListing> topics, String topicName){
        if (topics == null){
            return false;
        }
        return topics.stream().anyMatch(topic->topic.name().equals(topicName));
    }

    private void checkMaxRetry(int retry, Integer maxRetry) {
        if (retry > maxRetry) {
            throw new KafkaClientException("Reached max number of retry for reading kafka topic(s)!");
        }
    }

    private void sleep(Long sleepTimes){
        try {
            Thread.sleep(sleepTimes);
        }catch(InterruptedException e){
            throw new KafkaClientException("Error while sleeping for waiting new created topics!!");
        }
    }


    private Collection<TopicListing> getTopics(){
        Collection<TopicListing> topics;
        try{
            topics = retryTemplate.execute(this::doGetTopics);
        }catch(Throwable t){
            throw new KafkaClientException("Reached max number of retry for reading kafka topic(s)!", t);
        }
        return topics;

    }



    private Collection<TopicListing> doGetTopics(RetryContext retryContext) throws ExecutionException, InterruptedException {
        Collection<TopicListing> topics = adminClient.listTopics().listings().get();
        LOG.info("Reading kafka topic {}, attempt {}", kafkaConfigData.getTopicNamesToCreate().toArray(),retryContext.getRetryCount());

        if (topics != null){
            topics.forEach(topic -> LOG.debug("Topic with name {}", topic.name()));
        }
        return topics;
    }

    public void checkSchemaRegistry(){
        int retryCount = 1;
        Integer maxRetry = retryConfigData.getMaxAttempts();
        Long sleepTimes = retryConfigData.getSleepTimeMs();
        int multiplier = retryConfigData.getMultiplier().intValue();
        while(!getSchemaRegistryStatus().is2xxSuccessful()){
            checkMaxRetry(retryCount++, maxRetry);
            sleep(sleepTimes);
            sleepTimes *= multiplier;
        }
    }

    private HttpStatusCode getSchemaRegistryStatus(){
        try{
            return webClient
                    .method(HttpMethod.GET)
                    .uri(kafkaConfigData.getSchemaRegistryUrl())
                    .exchangeToMono(response -> {
                        if (response.statusCode().is2xxSuccessful()){
                            return Mono.just(response.statusCode());
                        } else{
                            return Mono.just(HttpStatus.SERVICE_UNAVAILABLE);
                        }
                    }).block();
        } catch (Exception e) {
            return HttpStatus.SERVICE_UNAVAILABLE;
        }
    }


}

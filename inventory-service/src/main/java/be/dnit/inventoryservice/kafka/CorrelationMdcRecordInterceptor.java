package be.dnit.inventoryservice.kafka;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.slf4j.MDC;
import org.springframework.kafka.listener.RecordInterceptor;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * Kafka {@link RecordInterceptor} that reads the {@code X-Correlation-Id} header
 * from every incoming consumer record and sets it in the SLF4J MDC under the key
 * {@code correlationId}. Spring Boot auto-configuration picks up a single
 * {@code RecordInterceptor} bean and applies it to the default listener container
 * factory, so this interceptor runs for all {@code @KafkaListener} methods in
 * inventory-service without any changes to business code.
 *
 * <p>MDC is cleared in {@link #afterRecord} which is called in a finally-block
 * after success or failure, ensuring no thread-local leakage between records.
 */
@Component
public class CorrelationMdcRecordInterceptor implements RecordInterceptor<Object, Object> {

    public static final String HEADER_NAME = "X-Correlation-Id";
    public static final String MDC_KEY = "correlationId";

    @Override
    public ConsumerRecord<Object, Object> intercept(ConsumerRecord<Object, Object> record,
                                                    Consumer<Object, Object> consumer) {
        Header header = record.headers().lastHeader(HEADER_NAME);
        if (header != null) {
            String correlationId = new String(header.value(), StandardCharsets.UTF_8);
            MDC.put(MDC_KEY, correlationId);
        }
        return record;
    }

    @Override
    public void afterRecord(ConsumerRecord<Object, Object> record,
                            Consumer<Object, Object> consumer) {
        MDC.remove(MDC_KEY);
    }
}

package yan0kom;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

import java.io.IOException;
import java.lang.reflect.Type;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

import static java.time.temporal.ChronoUnit.MILLIS;

@Slf4j
public class CrptApi {
    private static final int REQUEST_TIMEOUT_MILLIS = 3000;
    private static final URI API_METHOD_URI;
    static {
        try {
            API_METHOD_URI = new URI("https://ismp.crpt.ru/api/v3/lk/documents/create");
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    private final ThrottleHelper throttleHelper;

    private final HttpClient httpClient;
    private final Gson gson = new GsonBuilder()
            .registerTypeAdapter(LocalDate.class, new LocalDateSerializer())
            .create();

    public CrptApi(TimeUnit timeUnit, int requestLimit) {
        throttleHelper = new ThrottleHelper(timeUnit, requestLimit);
        httpClient = HttpClient.newBuilder().build();
    }

    public void createDocument(DocumentsCreateOutDto dto) {
        throttleHelper.throttle();
        log.debug("Executing request to CRPT API...");
        try {
            val response = postRequest(dto);
            log.debug("Response status code is {}", response.statusCode());
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }
    }

    private HttpResponse<String> postRequest(DocumentsCreateOutDto dto) throws IOException, InterruptedException {
        val request = HttpRequest.newBuilder()
                .uri(API_METHOD_URI)
                .header("Content-Type", "application/json")
                .method("POST", HttpRequest.BodyPublishers.ofString(gson.toJson(dto)))
                .timeout(Duration.of(REQUEST_TIMEOUT_MILLIS, MILLIS))
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    static class ThrottleHelper {
        private final TimeUnit timeUnit;
        private final int requestLimit;

        private final ReentrantLock lock = new ReentrantLock();
        private final Condition canPostRequest = lock.newCondition();

        private Instant resetInstant;
        private volatile int requestCount;

        ThrottleHelper(TimeUnit timeUnit, int requestLimit) {
            this.timeUnit = timeUnit;
            this.requestLimit = requestLimit;

            setStartInstant(Instant.EPOCH);
        }

        private void setStartInstant(Instant instant) {
            log.debug("Reset request count");
            resetInstant = instant.plus(1, timeUnit.toChronoUnit());
            requestCount = 0;
        }

        void throttle() {
            val requestInstant = Instant.now();
            resetIfElapsed(requestInstant);
            if (!waitForCanPostRequest(requestInstant)) {
                log.debug("Try again");
                throttle();
            }
        }

        private void resetIfElapsed(Instant requestInstant) {
            try {
                lock.lock();
                if (requestInstant.isAfter(resetInstant)) {
                    setStartInstant(requestInstant);
                    canPostRequest.signalAll();
                }
            } finally {
                lock.unlock();
            }
        }

        private boolean waitForCanPostRequest(Instant requestInstant) {
            try {
                lock.lock();
                if (incRequestCount()) {
                    return true;
                } else {
                    log.debug("Waiting (request count is {} of {})...", requestCount, requestLimit);
                    boolean reseted = canPostRequest.await(
                            resetInstant.toEpochMilli() - requestInstant.toEpochMilli() + 1,
                            TimeUnit.MILLISECONDS);
                    if (reseted) {
                        return incRequestCount();
                    }
                }
            } catch (InterruptedException e) {
                log.error(e.getMessage(), e);
            } finally {
                lock.unlock();
            }
            return false;
        }

        private boolean incRequestCount() {
            if (requestCount < requestLimit) {
                ++requestCount;
                log.debug("Request count is {} of {}", requestCount, requestLimit);
                return true;
            }
            return false;
        }
    }

    /** DTO for call to API */
    public record DocumentsCreateOutDto(
            Description description,
            String doc_id,
            String doc_status,
            String doc_type,
            Boolean importRequest,
            String owner_inn,
            String participant_inn,
            String producer_inn,
            LocalDate production_date,
            String production_type,
            List<Product> products,
            LocalDate reg_date,
            String reg_number

    ) {
        public record Description(String participantInn) {}
        public record Product(
                String certificate_document,
                LocalDate certificate_document_date,
                String certificate_document_number,
                String owner_inn,
                String producer_inn,
                LocalDate production_date,
                String tnved_code,
                String uit_code,
                String uitu_code
        ) {}
    }

    static class LocalDateSerializer implements JsonSerializer<LocalDate> {
        public JsonElement serialize(LocalDate date, Type typeOfSrc, JsonSerializationContext context) {
            return new JsonPrimitive(date.format(DateTimeFormatter.ISO_LOCAL_DATE)); // "yyyy-mm-dd"
        }
    }
}

package yan0kom;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.concurrent.TimeUnit;

import static java.time.temporal.ChronoUnit.MILLIS;

@Slf4j
public class CrptApiSingleThread {
    private static final int REQUEST_TIMEOUT_MILLIS = 1000;
    private static final URI API_METHOD_URI;
    static {
        try {
            API_METHOD_URI = new URI("https://ismp.crpt.ru/api/v3/lk/documents/create");
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    private final TimeUnit timeUnit;
    private final int requestLimit;

    private final HttpClient httpClient;
    private final Gson gson = new GsonBuilder()
            .registerTypeAdapter(LocalDate.class, new CrptApi.LocalDateSerializer())
            .create();

    private Instant startInstant;
    private Instant resetInstant;
    private int requestCount;

    public CrptApiSingleThread(TimeUnit timeUnit, int requestLimit) {
        this.timeUnit = timeUnit;
        this.requestLimit = requestLimit;

        httpClient = HttpClient.newBuilder().build();

        this.startInstant = Instant.EPOCH;
        this.resetInstant = startInstant.plus(1, timeUnit.toChronoUnit());
    }

    public void createDocument(CrptApi.DocumentsCreateOutDto dto) {
        throttle();
        log.debug("Executing request to CRPT API...");
        HttpResponse<String> response;
        try {
            response = postRequest(dto);
            log.debug("Response status code is {}", response.statusCode());
        } catch (Exception e) {
            log.debug(e.getMessage(), e);
        }
    }

    private void throttle() {
        val requestInstant = Instant.now();
        if (requestInstant.isAfter(resetInstant)) {
            log.debug("Reset request count");
            requestCount = 0;
            startInstant = Instant.now();
            resetInstant = startInstant.plus(1, timeUnit.toChronoUnit());
        }

        if (requestCount < requestLimit) {
            ++requestCount;
        } else {
            try {
                log.debug("Waiting...");
                Thread.sleep(resetInstant.toEpochMilli() - requestInstant.toEpochMilli() + 1);
                log.debug("Try again");
                throttle();
            } catch (InterruptedException e) {
                log.error(e.getMessage(), e);
            }
        }
    }

    private HttpResponse<String> postRequest(CrptApi.DocumentsCreateOutDto dto) throws IOException, InterruptedException {
        val request = HttpRequest.newBuilder()
                .uri(API_METHOD_URI)
                .header("Content-Type", "application/json")
                .method("POST", HttpRequest.BodyPublishers.ofString(gson.toJson(dto)))
                .timeout(Duration.of(REQUEST_TIMEOUT_MILLIS, MILLIS))
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }
}

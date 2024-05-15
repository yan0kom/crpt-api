package yan0kom;

import com.google.gson.GsonBuilder;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@Slf4j
public class Main {
    public static void main(String[] args) throws InterruptedException {
        val limit = 4;
        log.info("Limit rate to {} requests per second", limit);

        val gson = new GsonBuilder()
                .setPrettyPrinting()
                .registerTypeAdapter(LocalDate.class, new CrptApi.LocalDateSerializer())
                .create();

        val dto = new CrptApi.DocumentsCreateOutDto(
                new CrptApi.DocumentsCreateOutDto.Description("0123456789"),
                "123",
                "new",
                "LP_INTRODUCE_GOODS",
                true,
                "0123456789",
                "0123456789",
                "0123456789",
                LocalDate.of(2024, 5, 10),
                "SOME_TYPE",
                List.of(
                        new CrptApi.DocumentsCreateOutDto.Product(
                                "cert",
                                LocalDate.of(2024, 2, 10),
                                "39127",
                                "0123456789",
                                "0123456789",
                                LocalDate.of(2024, 5, 6),
                                "",
                                "",
                                ""
                        )
                ),
                LocalDate.of(2024, 4, 10),
                "941247"
        );
        log.debug(gson.toJson(dto));

        // single thread
        log.debug("*** Single thread ***");
        val stApi = new CrptApiSingleThread(TimeUnit.SECONDS, limit);
        for (int i = 1; i <= 15; i++) {
            stApi.createDocument(dto);
        }

        // multi thread
        log.debug("*** Multi thread ***");
        val mtApi = new CrptApi(TimeUnit.SECONDS, limit);
        val pool = Executors.newFixedThreadPool(3);
        Runnable task = () -> mtApi.createDocument(dto);
        for (int i = 1; i <= 15; i++) {
            pool.submit(task);
        }
        pool.shutdown();
        pool.awaitTermination(15, TimeUnit.SECONDS);
    }
}

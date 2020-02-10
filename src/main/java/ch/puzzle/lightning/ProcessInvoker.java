package ch.puzzle.lightning;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.ObservesAsync;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;

@ApplicationScoped
public class ProcessInvoker {

    Logger LOG = LoggerFactory.getLogger(ProcessInvoker.class);

    @ConfigProperty(name = "app.exec.path")
    Optional<String> appExecPath;

    @ConfigProperty(name = "app.beertap.name")
    String beerTapName;

    public void consumeInvoice(@ObservesAsync Invoice invoice) {
        CompletableFuture.supplyAsync(() -> {
            try {
                if (appExecPath.filter(Predicate.not(String::isBlank)).isPresent()) {
                    String command = appExecPath.get();
                    LOG.info("Executing command " + command);
                    return executeCommand(command, invoice);
                }
            } catch (IOException | InterruptedException e) {
                e.printStackTrace();
            }
            return 10;
        }).whenComplete((code, t) -> {
            if (t != null) {
                t.printStackTrace();
                LOG.error("Error while executing invocation", t);
            } else {
                LOG.info("return code " + code);
            }
        });
    }


    private Integer executeCommand(String command, Invoice invoice) throws IOException, InterruptedException {
        String productsArg = "--products=" + getFromMemo(invoice.memo);
        if(!invoice.memo.startsWith(beerTapName)) {
            LOG.info("Not a beerTap invoice");
            return 10;
        }

        LOG.info("Command: " + appExecPath + ", Args: " + productsArg);

        ProcessBuilder pb = new ProcessBuilder(command, productsArg);
        Map<String, String> env = pb.environment();
        pb.directory(Paths.get(".").toFile());
        Process p = pb.start();
        Integer returnCode = p.waitFor();

        BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));

        String line = "";
        while ((line = reader.readLine()) != null) {
            LOG.info(line);
        }

        return returnCode;
    }

    private String getFromMemo(String memo) {
        String[] memoParts = memo.split(" ");
        if (memoParts.length > 1) {
            String product = memoParts[1];
            LOG.info("Bought product: " + product);
            return product;
        }
        return "ERROR";
    }
}

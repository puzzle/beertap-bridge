package ch.puzzle.lightning;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.ObservesAsync;
import java.io.IOException;

@ApplicationScoped
public class ProcessInvoker {

    @ConfigProperty(name = "app.exec.path", defaultValue = "echo")
    String appExecPath;

    public void consumeInvoice(@ObservesAsync Invoice invoice) {
        try {
            ProcessBuilder pb = new ProcessBuilder(appExecPath, invoice.memo);
            pb.redirectOutput(ProcessBuilder.Redirect.INHERIT);
            pb.redirectError(ProcessBuilder.Redirect.INHERIT);
            Process process = pb.start();
            int exit = process.waitFor();
            System.out.println("process exited with value " + exit);
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
    }
}

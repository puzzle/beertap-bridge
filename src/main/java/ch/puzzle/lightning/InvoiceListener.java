package ch.puzzle.lightning;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import javax.annotation.PostConstruct;
import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.context.Destroyed;
import javax.enterprise.context.Initialized;
import javax.enterprise.event.Event;
import javax.enterprise.event.Observes;
import javax.enterprise.event.ObservesAsync;
import javax.inject.Inject;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.sse.InboundSseEvent;
import javax.ws.rs.sse.SseEventSource;
import java.util.concurrent.TimeUnit;

@ApplicationScoped
public class InvoiceListener {

    @ConfigProperty(name = "app.ln.invoice.sse-url")
    String invoiceSseUrl;

    private SseEventSource source;

    @Inject
    Event<Invoice> invoiceEvent;

    @Inject
    Event<SubscriptionClosed> subscriptionClosedEvent;

    private boolean running;

    @PostConstruct
    void init() {
        running = true;
        subscribeToInvoices();
    }

    private void accept(InboundSseEvent inboundSseEvent) {
        Invoice invoice = inboundSseEvent.readData(Invoice.class, MediaType.APPLICATION_JSON_TYPE);
        if (invoice.settled) {
            invoiceEvent.fireAsync(invoice);
        }
    }

    public void init(@Observes @Initialized(ApplicationScoped.class) Object init) {
    }

    public void destroy(@Observes @Destroyed(ApplicationScoped.class) Object init) {
        running = false;
        if (source != null) {
            source.close();
        }
    }

    private void subscribeToInvoices() {
        System.out.println("subscribing to invoices");
        Client client = ClientBuilder.newClient();
        WebTarget target = client.target(invoiceSseUrl);
        if (source != null) {
            source.close();
        }
        source = SseEventSource.target(target).reconnectingEvery(1, TimeUnit.SECONDS).build();
        source.register(this::accept, this::onError, this::onComplete);
        source.open();
    }

    public void onSubscriptionComplete(@ObservesAsync SubscriptionClosed event) {
        if (this.running) {
            try {
                TimeUnit.SECONDS.sleep(5L);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            subscribeToInvoices();
        }
    }

    private void onError(Throwable throwable) {
        throwable.printStackTrace();
    }

    private void onComplete() {
        System.out.println("Subscription complete");
        if (this.running) {
            subscriptionClosedEvent.fireAsync(new SubscriptionClosed());
        }
    }

    private class SubscriptionClosed {

    }
}

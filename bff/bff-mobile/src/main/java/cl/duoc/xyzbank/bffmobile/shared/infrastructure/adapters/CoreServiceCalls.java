package cl.duoc.xyzbank.bffmobile.shared.infrastructure.adapters;

import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.util.function.Supplier;

public final class CoreServiceCalls {

    private CoreServiceCalls() {
    }

    public static <T> T fetch(Supplier<T> request) {
        try {
            T response = request.get();
            if (response == null) {
                throw new CoreServiceCallException(502, "Core service returned an empty response");
            }
            return response;
        } catch (RestClientResponseException exception) {
            throw new CoreServiceCallException(exception.getStatusCode().value(), exception.getStatusText());
        } catch (RestClientException exception) {
            throw new CoreServiceCallException(503, "Core service is unavailable");
        }
    }
}

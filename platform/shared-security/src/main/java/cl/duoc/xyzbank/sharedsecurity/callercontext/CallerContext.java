package cl.duoc.xyzbank.sharedsecurity.callercontext;

import java.util.Optional;
import java.util.Set;

public interface CallerContext {

    String customerId();

    Channel channel();

    Set<String> scopes();

    Optional<String> terminalId();
}

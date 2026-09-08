package cl.duoc.xyzbank.bffatm.shared.infrastructure.rest;

import cl.duoc.xyzbank.sharedsecurity.callercontext.CallerContext;
import cl.duoc.xyzbank.sharedsecurity.callercontext.CallerIdentityException;
import cl.duoc.xyzbank.sharedsecurity.callercontext.Channel;
import cl.duoc.xyzbank.sharedsecurity.callercontext.HeaderCallerContextAdapter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class CallerContextInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod)) {
            return true;
        }
        CallerContext callerContext = HeaderCallerContextAdapter.resolve(
                request.getHeader("X-Customer-Id"),
                request.getHeader("X-Channel"),
                request.getHeader("X-Terminal-Id"));
        if (callerContext.channel() != Channel.ATM) {
            throw CallerIdentityException.forbidden("This endpoint requires the atm channel");
        }
        request.setAttribute(CallerContext.class.getName(), callerContext);
        return true;
    }
}
